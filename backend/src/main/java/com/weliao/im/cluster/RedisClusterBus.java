package com.weliao.im.cluster;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * 基于 Redis Pub/Sub 的跨节点总线。
 * <p>
 * 通道设计：<b>每个节点订阅自己的一条通道</b>（{@code im:ws:<nodeId>}），发送方按目标精确投递。
 * <p>
 * 为什么不是「所有节点订阅同一条广播通道」：
 * <ul>
 *   <li>广播通道下每台机器都要收到全站消息、再各自判断「这条是不是给我的」，扇出是 N 倍</li>
 *   <li>精确通道下一条消息只会送到真正持有该连接的节点，投递成本与「目标用户所在的节点数」成正比</li>
 * </ul>
 * 代价是发送方必须先查一次在线登记表——这正是 {@link PresenceStore} 存在的理由。
 * <p>
 * 另一个容易被追问的坑：节点自己订阅自己，会不会收到自己发的消息？这里不会，因为
 * {@code WsHub} 只对「非本机节点」调用 publish。就算将来有人误发到本机通道，接收侧走的也是
 * 本地投递，不会再次转发，构不成回环。
 */
public class RedisClusterBus implements ClusterBus, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RedisClusterBus.class);

    private static final String CHANNEL_PREFIX = "im:ws:";
    /** 运行时 Redis 掉线时的日志限流间隔，避免每条消息刷一行 WARN */
    private static final long WARN_INTERVAL_MS = 10_000L;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final String nodeId;
    private final AtomicLong lastWarnAt = new AtomicLong(0);

    private RedisMessageListenerContainer container;
    private volatile boolean started;

    public RedisClusterBus(StringRedisTemplate redis, ObjectMapper objectMapper, String nodeId) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.nodeId = nodeId;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    /**
     * 开始接收投递到本节点的消息。
     *
     * @param deliver 收到消息后的回调：(目标用户, 信封 JSON)。由 {@code WsHub} 提供，只做本地投递
     */
    public void start(BiConsumer<Long, String> deliver) {
        if (started) {
            return;
        }
        RedisMessageListenerContainer c = new RedisMessageListenerContainer();
        c.setConnectionFactory(redis.getConnectionFactory());
        MessageListener listener = (message, pattern) ->
                onMessage(new String(message.getBody(), StandardCharsets.UTF_8), deliver);
        c.addMessageListener(listener, new ChannelTopic(CHANNEL_PREFIX + nodeId));
        c.afterPropertiesSet();
        c.start();
        this.container = c;
        this.started = true;
        log.info("跨节点投递已启动：本节点订阅 {}", CHANNEL_PREFIX + nodeId);
    }

    @Override
    public void publish(String targetNodeId, long targetUserId, String json) {
        if (targetNodeId == null || targetNodeId.isBlank()) {
            return;
        }
        try {
            String body = objectMapper.writeValueAsString(new Push(targetUserId, json));
            redis.convertAndSend(CHANNEL_PREFIX + targetNodeId, body);
        } catch (Exception e) {
            // Redis 临时不可用不应该打断业务线程：实时推送丢掉，重连后客户端会通过接口补齐
            if (System.currentTimeMillis() - lastWarnAt.get() > WARN_INTERVAL_MS) {
                lastWarnAt.set(System.currentTimeMillis());
                log.warn("跨节点投递失败 node={} user={}: {}", targetNodeId, targetUserId, e.getMessage());
            }
        }
    }

    private void onMessage(String body, BiConsumer<Long, String> deliver) {
        try {
            JsonNode root = objectMapper.readTree(body);
            long userId = root.path("u").asLong(0L);
            String payload = root.path("p").asText("");
            if (userId <= 0 || payload.isEmpty()) {
                return;
            }
            deliver.accept(userId, payload);
        } catch (Exception e) {
            log.debug("无法解析跨节点消息: {}", e.getMessage());
        }
    }

    @Override
    public void close() {
        if (container != null) {
            try {
                container.stop();
                container.destroy();
            } catch (Exception e) {
                log.debug("关闭跨节点监听容器失败: {}", e.getMessage());
            }
        }
        started = false;
    }

    /** 线上传输格式：字段名压到 1 个字符，一条消息省几十字节，量大时不是小数 */
    private record Push(long u, String p) {
    }
}
