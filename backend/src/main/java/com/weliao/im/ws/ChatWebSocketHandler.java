package com.weliao.im.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weliao.im.common.BizException;
import com.weliao.im.common.RateLimiter;
import com.weliao.im.config.AppProperties;
import com.weliao.im.dto.SendMessageRequest;
import com.weliao.im.service.ConversationService;
import com.weliao.im.service.MessageService;
import com.weliao.im.vo.MessageVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WebSocket 业务入口。
 * <p>
 * 客户端帧：ping / send / read
 * 服务端帧：ready / pong / ack / message / read / presence / friend_* / conversation_update / error
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int SEND_BUFFER_LIMIT = 512 * 1024;

    private final ObjectMapper objectMapper;
    private final WsHub hub;
    private final PresenceService presenceService;
    private final MessageService messageService;
    private final ConversationService conversationService;
    private final AppProperties appProperties;
    private final RateLimiter rateLimiter;

    public ChatWebSocketHandler(ObjectMapper objectMapper, WsHub hub,
                                PresenceService presenceService,
                                MessageService messageService,
                                ConversationService conversationService,
                                AppProperties appProperties,
                                RateLimiter rateLimiter) {
        this.objectMapper = objectMapper;
        this.hub = hub;
        this.presenceService = presenceService;
        this.messageService = messageService;
        this.conversationService = conversationService;
        this.appProperties = appProperties;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("未认证"));
            return;
        }
        // 包装成并发安全的会话，多线程同时推送不会乱序
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT);
        hub.register(userId, safe);
        hub.sendTo(userId, WsEnvelope.of("ready", Map.of("userId", userId)));
        presenceService.broadcast(userId, true);
        log.debug("WS 建连 user={} session={}", userId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            return;
        }
        // 任何一帧都算「这条连接还活着」，顺手给在线状态续期。
        // 多实例部署时，这决定了别的节点能不能继续把消息路由到本机。
        hub.heartbeat(userId);
        JsonNode root;
        try {
            root = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            hub.sendTo(userId, WsEnvelope.of("error", Map.of("message", "消息格式不正确")));
            return;
        }
        String type = root.path("type").asText("");
        JsonNode data = root.path("data");

        try {
            switch (type) {
                case "ping" -> hub.sendTo(userId, WsEnvelope.of("pong",
                        Map.of("serverTime", LocalDateTime.now().toString())));
                case "send" -> handleSend(userId, data);
                case "recall" -> handleRecall(userId, data);
                case "read" -> handleRead(userId, data);
                default -> hub.sendTo(userId, WsEnvelope.of("error", Map.of("message", "未知的消息类型")));
            }
        } catch (BizException e) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("clientMsgId", data.path("clientMsgId").asText(null));
            payload.put("message", e.getMessage());
            hub.sendTo(userId, WsEnvelope.of("error", payload));
        } catch (Exception e) {
            log.error("WS 处理失败 type={} user={}", type, userId, e);
            hub.sendTo(userId, WsEnvelope.of("error", Map.of("message", "服务器处理失败")));
        }
    }

    private void handleSend(Long userId, JsonNode data) {
        if (!data.isObject()) {
            throw BizException.badRequest("缺少消息内容");
        }
        long conversationId = data.path("conversationId").asLong(0L);
        if (conversationId <= 0) {
            throw BizException.badRequest("缺少会话 id");
        }
        // WS 通道也要限流，否则绕过 REST 的限制就能刷屏
        AppProperties.RateLimit cfg = appProperties.getRateLimit();
        if (cfg.isEnabled()
                && !rateLimiter.tryAcquire("ws-msg:" + userId, cfg.getMessagePerMinute(), java.time.Duration.ofMinutes(1))) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("clientMsgId", data.path("clientMsgId").asText(null));
            payload.put("message", "发送太频繁，请稍后再试");
            hub.sendTo(userId, WsEnvelope.of("error", payload));
            return;
        }

        String clientMsgId = data.path("clientMsgId").asText(null);
        SendMessageRequest req = objectMapper.convertValue(data, SendMessageRequest.class);
        MessageVO vo = messageService.send(userId, conversationId, req);

        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("clientMsgId", clientMsgId);
        ack.put("conversationId", conversationId);
        ack.put("message", vo);
        hub.sendTo(userId, WsEnvelope.of("ack", ack));
    }

    private void handleRecall(Long userId, JsonNode data) {
        long conversationId = data.path("conversationId").asLong(0L);
        long messageId = data.path("messageId").asLong(0L);
        if (conversationId <= 0 || messageId <= 0) {
            throw BizException.badRequest("撤回参数不完整");
        }
        messageService.recall(userId, conversationId, messageId);
    }

    private void handleRead(Long userId, JsonNode data) {
        long conversationId = data.path("conversationId").asLong(0L);
        long seq = data.path("seq").asLong(0L);
        if (conversationId <= 0) {
            return;
        }
        conversationService.markRead(userId, conversationId, seq);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        hub.unregister(session).ifPresent(offlineUserId -> {
            presenceService.broadcast(offlineUserId, false);
            log.debug("WS 断开 user={} status={}", offlineUserId, status);
        });
        if (userId == null) {
            log.trace("WS 关闭（未认证连接）session={}", session.getId());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        // 这里必须把 exception 本体传给 logger，不能只打 getMessage()：
        // 容器层的传输错误经常是 message 为 null 的 EOFException / IOException，
        // 只打 message 等于什么线索都没留下（排查跨节点推送时踩过一次）。
        log.debug("WS 传输错误 session={}", session.getId(), exception);
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (Exception ignored) {
            // 忽略
        }
    }
}
