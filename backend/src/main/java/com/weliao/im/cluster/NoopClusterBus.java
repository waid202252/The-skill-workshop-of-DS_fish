package com.weliao.im.cluster;

/**
 * 单机版总线：什么都不做。
 * <p>
 * 单机部署时所有连接都在本进程里，{@code WsHub} 本地投递已经覆盖了全部情况，
 * 不需要任何跨节点动作。保留这个实现是为了让调用方不用到处写 if——有没有跨节点能力
 * 由 {@link #enabled()} 一处表达。
 */
public class NoopClusterBus implements ClusterBus {

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public void publish(String targetNodeId, long targetUserId, String json) {
        // 单机模式下不该被调用；真被调用了说明有人误用，静默忽略最安全
    }
}
