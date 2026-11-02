package com.weliao.im.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 限流器门面：判断「这次请求还让不让过」，不关心计数存在哪。
 * <p>
 * 接口保持和改造前一模一样（{@code tryAcquire} / {@code retryAfterSeconds}），
 * 所以 {@code RateLimitFilter} 和 {@code ChatWebSocketHandler} 一行都不用改——
 * 这也是「先把存储抽象出来」的价值：换实现不动调用方。
 * <p>
 * 存储由 {@code config.RateLimitConfig} 注入：
 * <ul>
 *   <li>Redis 可用 → {@code RedisRateLimitStore}，多实例共享同一份计数，全局限流真正生效</li>
 *   <li>Redis 不可用 → {@link InMemoryRateLimitStore}，退化成改造前的单机行为</li>
 * </ul>
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final RateLimitStore store;

    public RateLimiter(RateLimitStore store) {
        this.store = store;
        log.info("限流计数存储: {}", store.kind());
    }

    /**
     * 尝试获取一次配额。
     *
     * @param key    限流维度（例如 {@code auth:1.2.3.4}、{@code msg:42}）
     * @param limit  窗口内允许的最大次数，<= 0 表示不限制
     * @param window 窗口长度
     * @return true 表示放行
     */
    public boolean tryAcquire(String key, int limit, Duration window) {
        if (limit <= 0) {
            return true;
        }
        return store.increment(key, window.toMillis()) <= limit;
    }

    /** 窗口剩余秒数，用于 Retry-After 响应头 */
    public long retryAfterSeconds(Duration window) {
        long windowMs = Math.max(1000L, window.toMillis());
        long now = System.currentTimeMillis();
        return Math.max(1, (windowMs - (now % windowMs)) / 1000);
    }

    /** 供启动日志与 {@code /api/system/info} 自检：当前用的是哪种存储 */
    public String storeKind() {
        return store.kind();
    }

    /** 供测试与运维观察：当前追踪的 key 数量（Redis 实现返回 -1） */
    public int trackedKeys() {
        return store.size();
    }
}
