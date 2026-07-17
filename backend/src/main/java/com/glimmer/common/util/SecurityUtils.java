package com.glimmer.common.util;

import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具类：从 SecurityContext 获取当前登录用户ID
 * JWT 过滤器将 userId（Long）作为 principal 存入 SecurityContext
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 获取当前登录用户ID，未登录抛 401
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }
        if (principal instanceof Number) {
            return ((Number) principal).longValue();
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}
