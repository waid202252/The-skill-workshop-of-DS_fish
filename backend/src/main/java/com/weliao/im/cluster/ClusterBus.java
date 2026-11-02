package com.weliao.im.cluster;

/**
 * 跨节点消息总线：把一条已经序列化好的 WebSocket 信封，投递给「目标用户所连接的那个节点」。
 * <p>
 * 这里刻意只做两个动作，不掺业务：
 * <ul>
 *   <li>发送方先用自己的 {@link PresenceStore} 查出目标用户在哪些节点上，再逐节点投递</li>
 *   <li>接收方收到后只推给自己本机的连接，<b>绝不能再次调用 {@code sendToAll}</b>，否则会在
 *       节点之间来回反弹，形成投递死循环</li>
 * </ul>
 * 顺带一个常被追问的点：Redis Pub/Sub 是 at-most-once，发布出去时订阅方掉线，这条消息就没了。
 * 对「在线状态的实时提醒」这种可丢的消息够用；对聊天消息不够用——那要靠落库 + 客户端重连拉
 * 增量（本项目走的正是后者），所以这里丢消息不影响一致性，只影响实时性。
 */
public interface ClusterBus {

    /** 是否有跨节点投递能力。false 时 {@link #publish} 是空操作。 */
    boolean enabled();

    /**
     * 投给指定节点。
     *
     * @param targetNodeId 目标节点标识
     * @param targetUserId 目标用户（接收方据此决定推给本机的哪些连接）
     * @param json         已经序列化好的信封 JSON，接收方原样转发，避免二次序列化的开销和失真
     */
    void publish(String targetNodeId, long targetUserId, String json);
}
