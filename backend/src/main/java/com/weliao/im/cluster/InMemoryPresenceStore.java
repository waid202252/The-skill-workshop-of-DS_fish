package com.weliao.im.cluster;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单机版在线状态登记表：行为和改造前的 {@code WsHub} 内存表一致。
 * <p>
 * 存在的意义是「没有 Redis 也要能跑」。面试时这也是个可讲的点：
 * 跨节点能力是可插拔的，没 Redis 时功能降级而不是启动失败——本地开发零外部依赖这件事被保住了。
 */
public class InMemoryPresenceStore implements PresenceStore {

    /** userId → (nodeId → 最后心跳毫秒) */
    private final Map<Long, Map<String, Long>> table = new ConcurrentHashMap<>();
    private final long ttlMillis;

    public InMemoryPresenceStore(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    @Override
    public void touch(long userId, String nodeId) {
        table.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                .put(nodeId, System.currentTimeMillis());
    }

    @Override
    public void remove(long userId, String nodeId) {
        Map<String, Long> nodes = table.get(userId);
        if (nodes == null) {
            return;
        }
        nodes.remove(nodeId);
        if (nodes.isEmpty()) {
            table.remove(userId, nodes);
        }
    }

    @Override
    public Set<String> nodesOf(long userId) {
        Map<String, Long> nodes = table.get(userId);
        if (nodes == null || nodes.isEmpty()) {
            return Set.of();
        }
        long deadline = System.currentTimeMillis() - ttlMillis;
        Set<String> alive = ConcurrentHashMap.newKeySet(nodes.size());
        nodes.forEach((node, seenAt) -> {
            if (seenAt != null && seenAt >= deadline) {
                alive.add(node);
            }
        });
        return alive;
    }

    @Override
    public void sweep() {
        long deadline = System.currentTimeMillis() - ttlMillis;
        table.forEach((userId, nodes) -> {
            nodes.entrySet().removeIf(e -> e.getValue() == null || e.getValue() < deadline);
            if (nodes.isEmpty()) {
                table.remove(userId, nodes);
            }
        });
    }

    @Override
    public int size() {
        return table.size();
    }
}
