package com.weliao.im.cluster;

import java.util.Set;

/**
 * 在线状态登记表：记录「某个用户此刻连在哪几个节点上」。
 * <p>
 * 这是跨节点推送的第一层。重写 {@code WsHub} 之前，在线状态只存在于某个 JVM 的
 * {@code ConcurrentHashMap} 里——本质上就是「用户连在我这台机器上」这个事实只写在本机，
 * 别的实例要用（比如给 A 推 B 的在线状态）就得靠猜。改成登记表之后，这个事实是全局共享的。
 * <p>
 * 两个实现：
 * <ul>
 *   <li>{@link InMemoryPresenceStore}：单机，行为和改造前一致</li>
 *   <li>{@link RedisPresenceStore}：多实例，Redis 里带 TTL 的 Hash</li>
 * </ul>
 * 由 {@link ClusterRuntime} 在启动时按配置和 Redis 连通性决定用哪个。
 */
public interface PresenceStore {

    /**
     * 记录 / 续期：用户在这个节点上有一条活跃连接。
     * <p>
     * 建连时调一次，之后每次心跳再调一次——续期就是「我还活着」的全部含义，
     * 没有单独的「下线通知」协议要维护。
     */
    void touch(long userId, String nodeId);

    /** 用户在这个节点的最后一条连接已经断开（正常关闭时才走到这里）。 */
    void remove(long userId, String nodeId);

    /**
     * 该用户此刻所在的节点集合，空集合表示离线。
     * <p>
     * 返回的不包含本机时可以不走跨节点转发，这是省掉一次无意义发布的地方。
     */
    Set<String> nodesOf(long userId);

    /** 是否在线（任意节点在线即算在线），给好友列表的在线小圆点用。 */
    default boolean isOnline(long userId) {
        return !nodesOf(userId).isEmpty();
    }

    /** 清理过期记录。内存实现必须调，Redis 实现调它只是减少垃圾字段。 */
    default void sweep() {
    }

    /** 当前登记的用户数，供启动日志和运维观察。 */
    int size();
}
