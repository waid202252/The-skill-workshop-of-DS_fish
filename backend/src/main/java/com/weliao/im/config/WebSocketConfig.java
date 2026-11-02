package com.weliao.im.config;

import com.weliao.im.ws.ChatWebSocketHandler;
import com.weliao.im.ws.WsHandshakeInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * 原生 WebSocket 端点：/ws?token=xxx
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final WsHandshakeInterceptor handshakeInterceptor;

    public WebSocketConfig(ChatWebSocketHandler chatWebSocketHandler,
                           WsHandshakeInterceptor handshakeInterceptor) {
        this.chatWebSocketHandler = chatWebSocketHandler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }

    /**
     * 调大文本帧缓冲区：一条 5000 字的文本消息 UTF-8 编码后轻松超过容器默认的 8KB，
     * 不调整会导致连接被容器直接关闭。
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(64 * 1024);
        container.setMaxBinaryMessageBufferSize(64 * 1024);
        // 客户端 25s 心跳，90s 收不到任何帧就认为连接已死，回收会话
        container.setMaxSessionIdleTimeout(90_000L);
        return container;
    }
}
