package com.glimmer.config.websocket;

import com.glimmer.common.util.JwtUtils;
import com.glimmer.config.security.LoginSessionManager;
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
 *
 * 同时校验单点登录会话（jti）：已在别处登录的旧会话不允许建立新连接。
 */
@Slf4j
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String WS_USER_ID_KEY = "wsUserId";
    /** 握手会话ID（jti），供 @MessageMapping 发消息时做单点登录校验 */
    public static final String WS_JTI_KEY = "wsJti";

    private final JwtUtils jwtUtils;
    private final LoginSessionManager loginSessionManager;

    public JwtHandshakeInterceptor(JwtUtils jwtUtils, LoginSessionManager loginSessionManager) {
        this.jwtUtils = jwtUtils;
        this.loginSessionManager = loginSessionManager;
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
                    // 单点登录校验：会话已被新登录顶替则拒绝握手
                    if (!loginSessionManager.isCurrentSession(userId, jwtUtils.getJti(token))) {
                        log.warn("WebSocket 握手拒绝：会话已在其他地方登录, userId={}", userId);
                        response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                        return false;
                    }
                    attributes.put(WS_USER_ID_KEY, userId);
                    attributes.put(WS_JTI_KEY, jwtUtils.getJti(token));
                    log.info("WebSocket 握手鉴权成功: userId={}", userId);
                    return true;
                }
            }
            // 游客模式（无 token）：允许连接但不注入 userId，仅可围观收消息
            log.info("WebSocket 游客模式连接：无 token，仅可围观");
            return true;
        }
        log.warn("WebSocket 握手鉴权失败：非 HTTP 请求");
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
