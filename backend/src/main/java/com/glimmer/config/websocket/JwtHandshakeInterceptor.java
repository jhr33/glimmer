package com.glimmer.config.websocket;

import com.glimmer.common.util.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * WebSocket 握手 JWT 鉴权拦截器
 * 从握手时的查询参数 token 中提取 JWT 并校验，将 userId 注入 WebSocket Session attributes
 * 连接地址示例：ws://host/ws-campfire?token=xxx
 */
@Slf4j
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String WS_USER_ID_KEY = "wsUserId";

    private final JwtUtils jwtUtils;

    public JwtHandshakeInterceptor(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        log.info("WebSocket 握手请求: {}", request.getURI());
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest servlet = servletRequest.getServletRequest();
            String token = servlet.getParameter("token");
            log.info("WebSocket token: {}", token != null ? token.substring(0, Math.min(20, token.length())) + "..." : "null");
            if (StringUtils.hasText(token) && jwtUtils.isValid(token)) {
                Long userId = jwtUtils.getUserId(token);
                if (userId != null) {
                    attributes.put(WS_USER_ID_KEY, userId);
                    log.info("WebSocket 握手鉴权成功: userId={}", userId);
                    return true;
                }
            }
        }
        log.warn("WebSocket 握手鉴权失败：token 无效或缺失");
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
