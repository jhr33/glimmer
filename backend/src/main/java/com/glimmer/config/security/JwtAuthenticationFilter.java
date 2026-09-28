package com.glimmer.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.glimmer.common.response.Result;
import com.glimmer.common.util.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT 认证过滤器
 *
 * 职责（只做认证，不做处罚拦截——系统封禁用户允许登录浏览/申诉，发言由 Service 层拦截）：
 * 1. 解析 Authorization: Bearer <token>
 * 2. 校验 JWT 签名/有效期
 * 3. 校验会话 jti 是否为 Redis 中的当前会话（单点登录：被新登录顶替的旧 token 直接拒绝，返回 4024）
 * 4. 通过后把 userId/role 注入 SecurityContext，供 Controller 通过 SecurityUtils 获取
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final LoginSessionManager loginSessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtAuthenticationFilter(JwtUtils jwtUtils, LoginSessionManager loginSessionManager) {
        this.jwtUtils = jwtUtils;
        this.loginSessionManager = loginSessionManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (StringUtils.hasText(token)) {
            Claims claims = jwtUtils.parseToken(token);
            if (claims != null) {
                Object userIdObj = claims.get("userId");
                Long userId = userIdObj instanceof Number ? ((Number) userIdObj).longValue() : null;
                String role = claims.get("role", String.class);
                String jti = claims.getId();

                if (userId != null) {
                    // 单点登录校验：jti 与 Redis 中当前会话不一致 → 已在别处登录，旧会话下线
                    if (!loginSessionManager.isCurrentSession(userId, jti)) {
                        writeSessionReplaced(response);
                        return;
                    }
                    // 角色统一加 ROLE_ 前缀并转大写，适配 Spring Security 的 hasRole()
                    // （DB 中角色存储为小写 'admin'，hasRole("ADMIN") 需要 ROLE_ADMIN）
                    List<SimpleGrantedAuthority> authorities =
                            List.of(new SimpleGrantedAuthority("ROLE_" + (role != null ? role.toUpperCase() : "USER")));
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userId, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头提取 token：Authorization: Bearer xxx
     */
    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }

    /**
     * 会话被顶替：HTTP 200 + 业务码 4024（与项目统一的业务错误响应风格一致），
     * 前端据此弹出"已在其他地方登录"提示并跳回登录页。
     */
    private void writeSessionReplaced(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.error(4024, "您的账号已在其他地方登录，您已被迫下线")));
    }
}
