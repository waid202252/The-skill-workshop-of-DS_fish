package com.weliao.im.common;

/**
 * 限流计数器的存储抽象：只负责「同一个窗口内这个 key 又出现了一次，现在总共几次」。
 * <p>
 * 为什么单独抽出来：单机版和 Redis 版的差别只有「计数存在哪」，
 * 而「什么算一次、窗口多长、超了怎么办」这些业务判断应该只有一份。
 * 于是算法留在 {@link RateLimiter}，存储换实现。
 * <p>
 * 两个实现：
 * <ul>
 *   <li>{@link InMemoryRateLimitStore}：{@code ConcurrentHashMap}，单机，零依赖</li>
 *   <li>{@code com.weliao.im.cluster.RedisRateLimitStore}：Redis {@code INCR + PEXPIRE}，
 *       多实例共享同一份计数</li>
 * </ul>
 * 由 {@code config.RateLimitConfig} 按 Redis 是否可用二选一装配。
 */
public interface RateLimitStore {

    /**
     * 计数 +1 并返回当前窗口内的累计次数。
     *
     * @param dimension 限流维度，例如 {@code auth:1.2.3.4}、{@code msg:42}
     * @param windowMs  窗口长度（毫秒），实现内部会兜底到至少 1000ms
     * @return 本次累加后的次数（首次调用返回 1）
     */
    long increment(String dimension, long windowMs);

    /** 当前追踪的 key 数量。Redis 实现无法低代价统计，返回 -1。 */
    default int size() {
        return -1;
    }

    /** 实现名，用于启动日志和 {@code /api/system/info} 自检 */
    String kind();
}
