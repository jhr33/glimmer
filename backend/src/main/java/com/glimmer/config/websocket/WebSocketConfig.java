package com.glimmer.config.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * WebSocket 配置（STOMP over WebSocket）
 * 见开发文档 §3.4.3 / §4.8.2
 * - 端点：/ws-campfire（SockJS 兜底）
 * - 应用前缀：/app
 * - 主题前缀：/topic、/queue
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    public WebSocketConfig(JwtHandshakeInterceptor jwtHandshakeInterceptor) {
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-campfire")
                .addInterceptors(jwtHandshakeInterceptor)
                .setHandshakeHandler(new DefaultHandshakeHandler() {
                    @Override
                    protected Principal determineUser(org.springframework.http.server.ServerHttpRequest request,
                                                      org.springframework.web.socket.WebSocketHandler wsHandler,
                                                      Map<String, Object> attributes) {
                        Object userId = attributes.get(JwtHandshakeInterceptor.WS_USER_ID_KEY);
                        if (userId == null) {
                            return null;
                        }
                        return () -> String.valueOf(userId);
                    }
                })
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 应用前缀：客户端发送消息到 /app/** 的由 @MessageMapping 处理
        registry.setApplicationDestinationPrefixes("/app");
        // 主题前缀：/topic 广播、/queue 点对点
        registry.enableSimpleBroker("/topic", "/queue");
    }
}
