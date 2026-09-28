package com.glimmer.config.security;

import com.glimmer.common.util.JwtUtils;
import com.glimmer.common.util.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 登录会话管理器（单点登录）
 *
 * 规则：同一账号同一时刻只允许一个有效会话，以最新登录为准。
 * 实现：每次登录生成随机 jti（JWT 的会话ID），写入 Redis（key 与用户绑定）；
 *      每次请求校验 token 中的 jti 是否等于 Redis 中的当前值，
 *      不一致说明该账号已在别处重新登录，旧会话立即失效（4024 被迫下线）。
 * TTL 与 JWT 有效期一致（30天），不做滑动续期。
 */
@Slf4j
@Component
public class LoginSessionManager {

    /** Redis key：login:session:{userId} -> 当前有效 jti */
    private static final String SESSION_KEY_PREFIX = "login:session:";

    private final RedisUtils redisUtils;
    private final JwtUtils jwtUtils;

    public LoginSessionManager(RedisUtils redisUtils, JwtUtils jwtUtils) {
        this.redisUtils = redisUtils;
        this.jwtUtils = jwtUtils;
    }

    private String sessionKey(Long userId) {
        return SESSION_KEY_PREFIX + userId;
    }

    /**
     * 登录成功后签发新会话：覆盖旧 jti（旧会话由此失效），返回新 jti
     */
    public String issueSession(Long userId) {
        String jti = JwtUtils.generateJti();
        redisUtils.set(sessionKey(userId), jti,
                Duration.ofMillis(jwtUtils.getExpirationMillis()));
        log.info("[会话] 用户登录签发新会话: userId={}, jti={}", userId, jti);
        return jti;
    }

    /**
     * 校验 token 所属会话是否仍是当前有效会话。
     *
     * @return true=当前会话有效；false=已被新登录顶替或会话不存在（过期/主动退出）
     */
    public boolean isCurrentSession(Long userId, String jti) {
        if (userId == null || jti == null || jti.isEmpty()) {
            return false;
        }
        return jti.equals(redisUtils.get(sessionKey(userId)));
    }

    /**
     * 主动退出登录：删除会话，token 立即失效
     */
    public void invalidate(Long userId) {
        if (userId != null) {
            redisUtils.delete(sessionKey(userId));
            log.info("[会话] 用户退出，会话已失效: userId={}", userId);
        }
    }
}
