package com.glimmer.config.security;

import com.glimmer.common.util.JwtUtils;
import com.glimmer.service.PunishmentService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 鉴权过滤器
 * 从 Authorization: Bearer {token} 中解析用户ID和角色，写入 SecurityContext
 * 同时检查用户是否被封禁（isUserBanned 有 Redis 缓存，5 分钟 TTL，性能影响可忽略）
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;
    private final PunishmentService punishmentService;

    public JwtAuthenticationFilter(JwtUtils jwtUtils, PunishmentService punishmentService) {
        this.jwtUtils = jwtUtils;
        this.punishmentService = punishmentService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (StringUtils.hasText(token) && jwtUtils.isValid(token)) {
            Claims claims = jwtUtils.parseToken(token);
            if (claims != null) {
                Long userId = jwtUtils.getUserId(token);
                String role = jwtUtils.getRole(token);
                if (userId != null) {
                    // 仅永久封禁（BAN）才拦截登录，禁言（MUTE）用户可登录但写操作被 Service 层拦截
                    if (punishmentService.isUserPermanentlyBanned(userId)) {
                        log.info("永久封禁用户请求被拦截: userId={}, uri={}", userId, request.getRequestURI());
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"code\":4015,\"message\":\"账号已被永久封禁\"}");
                        return;
                    }
                    String authority = "ROLE_" + (role == null ? "USER" : role.toUpperCase());
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userId, null, Collections.singletonList(new SimpleGrantedAuthority(authority)));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
