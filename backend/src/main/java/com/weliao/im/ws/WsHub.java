package com.weliao.im.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weliao.im.cluster.ClusterRuntime;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话登记中心：一个用户可以有多个连接（多端登录）。
 * <p>
 * 用 synchronized 保护单用户会话集合的增删，保证「首次上线 / 最后下线」判断准确。
 * <p>
 * <b>跨节点投递</b>（多实例部署时）：
 * <pre>
 * 发送方实例                                        接收方实例
 *   deliverLocal(userId)   命中就推，没命中也继续
 *   forwardRemote(userId)  ──查在线登记表──▶ 找到目标节点
 *                          ──PUBLISH────▶ im:ws:&lt;node&gt; ──▶ deliverRaw ──▶ 本机连接
 * </pre>
 * 关键约束：{@link #deliverRaw} 只做本地投递，绝不再调用 {@link #sendTo} / {@link #sendToAll}，
 * 否则两个实例之间会来回反弹，一条消息被无限复制。
 */
@Component
public class WsHub {

    private static final Logger log = LoggerFactory.getLogger(WsHub.class);

    private final Map<Long, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();
    private final Map<String, Long> userBySessionId = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final ClusterRuntime cluster;

    public WsHub(ObjectMapper objectMapper, ClusterRuntime cluster) {
        this.objectMapper = objectMapper;
        this.cluster = cluster;
    }

    /** 订阅本节点的投递通道。必须在能接收推送之前完成，所以放在初始化阶段。 */
    @PostConstruct
    void subscribeCluster() {
        cluster.startDelivery(this::deliverRaw);
    }

    /**
     * 注册连接。
     *
     * @return true 表示这是该用户在本机的第一条连接
     */
    public boolean register(Long userId, WebSocketSession session) {
        Set<WebSocketSession> set = sessionsByUser.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet());
        boolean first;
        synchronized (set) {
            first = set.isEmpty();
            set.add(session);
        }
        userBySessionId.put(session.getId(), userId);
        // 登记到全局在线表：别的实例要推消息给这个用户时，靠它找到本节点
        cluster.presence().touch(userId, cluster.nodeId());
        log.debug("WS 连接注册 user={} session={} first={} 当前连接数={}",
                userId, session.getId(), first, set.size());
        return first;
    }

    /**
     * 注销连接。
     *
     * @return 若这是该用户<b>全局</b>的最后一条连接（本机没有别的连接，其他节点上也没有），
     *         返回 userId 表示已离线；否则返回 empty
     */
    public Optional<Long> unregister(WebSocketSession session) {
        Long userId = userBySessionId.remove(session.getId());
        if (userId == null) {
            return Optional.empty();
        }
        Set<WebSocketSession> set = sessionsByUser.get(userId);
        boolean lastLocal = true;
        if (set != null) {
            synchronized (set) {
                set.removeIf(s -> s.getId().equals(session.getId()) || !s.isOpen());
                lastLocal = set.isEmpty();
                if (lastLocal) {
                    sessionsByUser.remove(userId, set);
                }
            }
        }
        if (!lastLocal) {
            // 本机还有同用户的其他连接（多端登录），在线状态不动
            return Optional.empty();
        }
        cluster.presence().remove(userId, cluster.nodeId());
        if (cluster.presence().isOnline(userId)) {
            // 本机掉线了，但用户在别的实例上还有连接——不算离线，不广播
            log.debug("WS 连接注销 user={} session={}，该用户在其他节点仍在线", userId, session.getId());
            return Optional.empty();
        }
        log.debug("WS 连接注销 user={} session={} 已离线", userId, session.getId());
        return Optional.of(userId);
    }

    /** 心跳续期。客户端 25s 一次 ping，漏掉的话在线状态会在 TTL 后自动过期。 */
    public void heartbeat(Long userId) {
        cluster.presence().touch(userId, cluster.nodeId());
    }

    /** 是否在线（任意节点在线即算在线）。 */
    public boolean isOnline(Long userId) {
        return cluster.presence().isOnline(userId);
    }

    public void sendTo(Long userId, WsEnvelope envelope) {
        String json = write(envelope);
        if (json == null) {
            return;
        }
        deliverLocal(userId, json);
        forwardRemote(userId, json);
    }

    public void sendToAll(Collection<Long> userIds, WsEnvelope envelope) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        String json = write(envelope);
        if (json == null) {
            return;
        }
        for (Long userId : userIds) {
            deliverLocal(userId, json);
            forwardRemote(userId, json);
        }
    }

    /**
     * 收到别的节点转发来的信封：只推本机连接。
     * <p>
     * 这是个「汇点」，绝不能是「转发点」——否则 A 推给 B，B 又推回 A，形成放大回环。
     */
    public void deliverRaw(Long userId, String json) {
        deliverLocal(userId, json);
    }

    /** 推给本机上该用户的全部连接。没有本机连接时静默返回。 */
    private void deliverLocal(Long userId, String json) {
        Set<WebSocketSession> set = sessionsByUser.get(userId);
        if (set == null || set.isEmpty()) {
            return;
        }
        for (WebSocketSession s : set) {
            sendRaw(s, json);
        }
    }

    /**
     * 跨节点转发：只投给「目标用户所在的、且不是本机」的节点。
     * <p>
     * 多端登录时一个用户可能同时挂在多个节点上，所以本地投递成功并不代表不用转发，
     * 这里不做「本地命中就返回」的短路。
     */
    private void forwardRemote(Long userId, String json) {
        if (!cluster.bus().enabled()) {
            return;
        }
        String self = cluster.nodeId();
        for (String node : cluster.presence().nodesOf(userId)) {
            if (!self.equals(node)) {
                cluster.bus().publish(node, userId, json);
            }
        }
    }

    private String write(WsEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            log.error("WS 信封序列化失败", e);
            return null;
        }
    }

    private void sendRaw(WebSocketSession session, String json) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.debug("WS 发送失败 session={}: {}", session.getId(), e.getMessage());
            try {
                session.close();
            } catch (Exception ignored) {
                // 关闭失败无需处理，等容器回收
            }
        }
    }
}
