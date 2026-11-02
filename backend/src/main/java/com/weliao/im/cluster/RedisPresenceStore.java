package com.weliao.im.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于 Redis 的在线状态登记表。
 * <p>
 * 数据结构：一个用户一个 Hash
 * <pre>
 * im:presence:42 = { "ws-host-a-3f2c1d8e" : 1761800000000,
 *                    "ws-host-b-91a4be07" : 1761799991000 }
 *                  └ 节点标识           └ 最后一次心跳的毫秒时间戳
 * </pre>
 * 两层过期，缺一不可：
 * <ol>
 *   <li><b>字段里的时间戳</b>负责「这个节点是不是还活着」。查询时过滤掉超过 TTL 的字段，
 *       所以实例被 kill -9 来不及 HDEL，最多 TTL 秒后它就被判定离线，不需要任何注册中心的心跳检测。</li>
 *   <li><b>Key 的 EXPIRE</b>负责「这个用户是不是彻底没了」。key 只在有活跃设备续期时才存在，
 *       用户所有设备都掉线后 key 自己消失，不会给 Redis 留一堆空 Hash。</li>
 * </ol>
 * 为什么不是「每个 (用户, 节点) 一个 key + TTL」：那样要枚举「这个用户在哪几个节点」就必须
 * SCAN 全库（Redis 没有按 key 前缀反查的正经办法），查询成本随数据量爆炸。用 Hash 是为了能
 * 一次 HGETALL 拿到全部节点。
 */
public class RedisPresenceStore implements PresenceStore {

    private static final Logger log = LoggerFactory.getLogger(RedisPresenceStore.class);

    /** 统一前缀，方便在 redis-cli 里 `SCAN 0 MATCH im:presence:*` 观察 */
    private static final String KEY_PREFIX = "im:presence:";

    private final StringRedisTemplate redis;
    private final long ttlMillis;

    public RedisPresenceStore(StringRedisTemplate redis, long ttlMillis) {
        this.redis = redis;
        this.ttlMillis = ttlMillis;
    }

    @Override
    public void touch(long userId, String nodeId) {
        String key = KEY_PREFIX + userId;
        // HSET 之后再 EXPIRE：只要还有设备在心跳，key 就一直活着
        redis.opsForHash().put(key, nodeId, Long.toString(System.currentTimeMillis()));
        redis.expire(key, Duration.ofMillis(ttlMillis));
    }

    @Override
    public void remove(long userId, String nodeId) {
        String key = KEY_PREFIX + userId;
        redis.opsForHash().delete(key, nodeId);
        Long left = redis.opsForHash().size(key);
        if (left == null || left == 0) {
            // 没有别的设备了，key 直接删掉，不等它自然过期
            redis.delete(key);
        }
    }

    @Override
    public Set<String> nodesOf(long userId) {
        Map<Object, Object> all = redis.opsForHash().entries(KEY_PREFIX + userId);
        if (all.isEmpty()) {
            return Set.of();
        }
        long deadline = System.currentTimeMillis() - ttlMillis;
        Set<String> nodes = new HashSet<>(Math.max(2, all.size()));
        for (Map.Entry<Object, Object> e : all.entrySet()) {
            if (isFresh(e.getValue(), deadline)) {
                nodes.add(String.valueOf(e.getKey()));
            }
        }
        return nodes;
    }

    /**
     * 扫掉过期字段。
     * <p>
     * 为什么用 SCAN 而不是 KEYS：KEYS 是 O(N) 且会阻塞整个 Redis（单线程），
     * 生产上等于拿一把大锁把所有业务请求堵在后头；SCAN 是游标分批，只占一个槽位的时间片。
     * <p>
     * 注意这个方法不是正确性必需的——{@link #nodesOf} 已经按时间戳过滤了，
     * 这里只是把「多设备下某个节点被 kill」留下的垃圾字段清掉。
     */
    @Override
    public void sweep() {
        long deadline = System.currentTimeMillis() - ttlMillis;
        ScanOptions options = ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(500).build();
        int removed = 0;
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                Map<Object, Object> fields = redis.opsForHash().entries(key);
                if (fields.isEmpty()) {
                    continue;
                }
                List<Object> stale = new ArrayList<>();
                for (Map.Entry<Object, Object> e : fields.entrySet()) {
                    if (!isFresh(e.getValue(), deadline)) {
                        stale.add(e.getKey());
                    }
                }
                if (stale.isEmpty()) {
                    continue;
                }
                redis.opsForHash().delete(key, stale.toArray());
                removed += stale.size();
                if (stale.size() == fields.size()) {
                    redis.delete(key);
                }
            }
        } catch (Exception e) {
            // 清理是尽力而为的后台任务，失败不能影响主流程
            log.debug("presence 清理失败: {}", e.getMessage());
            return;
        }
        if (removed > 0) {
            log.info("presence 清理完成，移除 {} 个离线节点字段", removed);
        }
    }

    @Override
    public int size() {
        int count = 0;
        ScanOptions options = ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(500).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                cursor.next();
                count++;
            }
        } catch (Exception e) {
            return 0;
        }
        return count;
    }

    /** 字段值是不是在有效期内。脏数据（非数字）一律当作过期，别让它卡住在线状态。 */
    private boolean isFresh(Object rawValue, long deadline) {
        if (rawValue == null) {
            return false;
        }
        try {
            return Long.parseLong(String.valueOf(rawValue)) >= deadline;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
