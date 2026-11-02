package com.weliao.im.common;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 单机版限流计数：固定窗口 + {@code ConcurrentHashMap}。
 * <p>
 * 窗口起点由 {@code now - now % windowMs} 算出，所以「同一分钟」这个窗口是世界时对齐的，
 * 不需要为每个 key 单独记一个起始时间戳。
 * <p>
 * 已知代价（两条都写在这里，避免上线后被问到时才发现）：
 * <ol>
 *   <li><b>窗口边界可能双倍突发</b>：59.9s 打满 60 次，60.0s 又能打满 60 次，瞬时是 120 次。
 *       换成滑动窗口（Redis ZSET 记时间戳）能消掉，代价是每个请求要 ZREMRANGEBYSCORE + ZCARD，
 *       内存和 CPU 都翻几倍——这个量级的业务不值得。</li>
 *   <li><b>多实例各算各的</b>：两个实例各允许 60 次，实际放行 120 次。
 *       要全局生效必须让所有实例数同一份，也就是换 {@code RedisRateLimitStore}。</li>
 * </ol>
 */
public class InMemoryRateLimitStore implements RateLimitStore {

    private record Window(long start, long count) {
    }

    private final Map<String, Window> buckets = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanup = new AtomicLong(0);

    @Override
    public long increment(String dimension, long windowMs) {
        long w = Math.max(1000L, windowMs);
        long now = System.currentTimeMillis();
        long start = now - (now % w);

        // compute 保证同 key 的读改写是原子的
        Window updated = buckets.compute(dimension, (k, prev) ->
                (prev == null || prev.start() != start)
                        ? new Window(start, 1)
                        : new Window(start, prev.count() + 1));

        cleanupIfNeeded(now, w);
        return updated.count();
    }

    @Override
    public int size() {
        return buckets.size();
    }

    @Override
    public String kind() {
        return "memory";
    }

    /** 每分钟最多清理一次，避免每次都遍历 */
    private void cleanupIfNeeded(long now, long windowMs) {
        long last = lastCleanup.get();
        if (now - last < 60_000L || !lastCleanup.compareAndSet(last, now)) {
            return;
        }
        long expireBefore = now - 2 * windowMs;
        Iterator<Map.Entry<String, Window>> it = buckets.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().start() < expireBefore) {
                it.remove();
            }
        }
    }
}
