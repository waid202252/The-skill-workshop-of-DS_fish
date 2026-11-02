package com.weliao.im.common;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 带容量上限的 TTL 缓存。
 * <p>
 * 为什么需要它：项目里原先用裸的 {@code ConcurrentHashMap} 做技能查询缓存和 README 缓存，
 * 只有"过期不读"、从不删除条目。查询词是用户输入的，长尾无限；
 * README 又是几十 KB 的文本——跑久了就是稳定的内存泄漏。
 * <p>
 * 这里做了三件事：
 * <ol>
 *   <li>读时判断过期，过期即删</li>
 *   <li>写入后若超过容量，先清掉所有已过期项</li>
 *   <li>还超标就按过期时间从早到晚淘汰约 1/4，把内存压回上限以内</li>
 * </ol>
 * 明确不做：LRU 链表（需要额外同步开销），对本项目这种"缓存只是省配额"的场景不划算。
 */
public final class TtlCache<K, V> {

    private record Entry<V>(V value, long expiresAt) {
    }

    private final int maxSize;
    private final ConcurrentHashMap<K, Entry<V>> map = new ConcurrentHashMap<>();

    public TtlCache(int maxSize) {
        this.maxSize = Math.max(1, maxSize);
    }

    /** 命中且未过期返回值，否则返回 null 并顺手清掉过期项 */
    public V get(K key) {
        Entry<V> entry = map.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt() <= System.currentTimeMillis()) {
            map.remove(key, entry);
            return null;
        }
        return entry.value();
    }

    public void put(K key, V value, long ttlMillis) {
        map.put(key, new Entry<>(value, System.currentTimeMillis() + Math.max(1L, ttlMillis)));
        if (map.size() > maxSize) {
            evict();
        }
    }

    public int size() {
        return map.size();
    }

    public void clear() {
        map.clear();
    }

    private void evict() {
        long now = System.currentTimeMillis();
        map.entrySet().removeIf(e -> e.getValue().expiresAt() <= now);
        int overflow = map.size() - maxSize;
        if (overflow <= 0) {
            return;
        }
        // 按过期时间升序淘汰最旧的一批（+1 是为了至少淘汰一条，避免反复触发）
        map.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> e.getValue().expiresAt()))
                .limit((long) overflow + maxSize / 4L)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(map::remove);
    }
}
