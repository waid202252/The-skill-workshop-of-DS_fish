package com.weliao.im.cluster;

import com.weliao.im.common.InMemoryRateLimitStore;
import com.weliao.im.common.RateLimitStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis 版限流计数：多实例共享同一份窗口计数。
 * <p>
 * 核心只有两行，但这两行分开写就是错的：
 * <pre>
 * INCR   im:rl:auth:1.2.3.4:1761800040000     → 3
 * PEXPIRE im:rl:auth:1.2.3.4:1761800040000 60000
 * </pre>
 * <b>为什么必须用 Lua 把两步包起来</b>：如果分两次发命令，第一次 INCR 成功、进程在这一瞬间
 * 崩了（或者网络断），key 就变成「永不过期」——每分钟每个维度泄漏一个 key，跑几天 Redis 就被打满，
 * 而且这些 key 的计数还永久卡住，导致该维度的人永远被限流。
 * 放进脚本后，INCR 和 PEXPIRE 在 Redis 单线程里连着执行，要么都生效要么都不生效。
 * <p>
 * 另一个细节：<b>只有 {@code count == 1} 时才设置过期</b>。每次 INCR 都去 PEXPIRE 会把窗口
 * 不断往后推，等于永远不过期（这是分布式限流最经典的一个坑）。
 * <p>
 * key 里带窗口起始时间戳（{@code 1761800040000}），所以「换窗口」这件事是天然发生的：
 * 新的一分钟就是新 key，不需要扫描或重置旧 key，旧 key 到期自己消失。
 */
public class RedisRateLimitStore implements RateLimitStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimitStore.class);

    /** 统一前缀，方便 redis-cli 里 {@code SCAN 0 MATCH im:rl:*} 观察 */
    private static final String KEY_PREFIX = "im:rl:";

    /**
     * KEYS[1] = 窗口 key，ARGV[1] = 本窗口剩余毫秒。
     * 返回累加后的计数。
     */
    private static final RedisScript<Long> INCR_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    /** Redis 故障时的降级计数（只在本机生效），保证不会因为缓存挂了把登录入口全打死 */
    private final InMemoryRateLimitStore fallback = new InMemoryRateLimitStore();
    private final StringRedisTemplate redis;
    private final AtomicLong lastWarnAt = new AtomicLong(0);

    /**
     * 熔断截止时间戳：失败后的一小段时间内直接走本机计数，不再去碰 Redis。
     * <p>
     * 为什么需要它：单纯 catch + 降级是可以运行的，但每个请求都要先等一次 Redis 超时
     * （默认 2s）才落到降级路径——登录接口从毫秒级变成 2 秒级，等于用另一种方式把服务打死。
     * 失败后跳过一小段时间，把「每次都等超时」压成「每分钟最多等几次」。
     */
    private final AtomicLong circuitOpenUntil = new AtomicLong(0);

    /** 熔断持续时间：够短以保证 Redis 恢复后很快接回去，够长以显著减少无效等待 */
    private static final long CIRCUIT_OPEN_MS = 5_000L;

    public RedisRateLimitStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long increment(String dimension, long windowMs) {
        long w = Math.max(1000L, windowMs);
        long now = System.currentTimeMillis();
        if (now < circuitOpenUntil.get()) {
            return fallback.increment(dimension, w);
        }
        long start = now - (now % w);
        long remainMs = Math.max(1L, w - (now - start));
        String key = KEY_PREFIX + dimension + ":" + start;
        try {
            Long count = redis.execute(INCR_SCRIPT, List.of(key), Long.toString(remainMs));
            if (count != null) {
                return count;
            }
        } catch (Exception e) {
            // 降级而不是 fail-open：仍然按本机计数拦，只是限得更松（多实例各自算），
            // 并且熔断一段时间，避免每个请求都白等一次超时。
            circuitOpenUntil.set(System.currentTimeMillis() + CIRCUIT_OPEN_MS);
            if (System.currentTimeMillis() - lastWarnAt.get() > 10_000L) {
                lastWarnAt.set(System.currentTimeMillis());
                log.warn("限流计数走 Redis 失败，已降级为本机计数 {}ms: {}", CIRCUIT_OPEN_MS, e.getMessage());
            }
        }
        return fallback.increment(dimension, w);
    }

    @Override
    public String kind() {
        return "redis";
    }
}
