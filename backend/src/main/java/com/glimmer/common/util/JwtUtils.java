package com.glimmer.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类（HS256，见开发文档 §4.3）
 * 密钥通过环境变量 JWT_SECRET 配置，过期时间 24 小时
 */
public class JwtUtils {

    private final SecretKey key;
    private final long expirationMillis;

    public JwtUtils(String secret, long expirationMillis) {
        // 密钥长度需 >= 256bit (32 字节)，不足时补齐
        byte[] keyBytes = padSecret(secret);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMillis = expirationMillis;
    }

    private byte[] padSecret(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length >= 32) {
            return bytes;
        }
        // 不足 32 字节则重复填充
        byte[] padded = new byte[32];
        for (int i = 0; i < 32; i++) {
            padded[i] = bytes[i % bytes.length];
        }
        return padded;
    }

    /**
     * 生成 JWT token
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param role     角色
     */
    public String generateToken(Long userId, String username, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMillis);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("userId", userId)
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * 解析 token，返回 Claims；无效或过期返回 null
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        Object userId = claims.get("userId");
        if (userId instanceof Number) {
            return ((Number) userId).longValue();
        }
        return null;
    }

    public String getRole(String token) {
        Claims claims = parseToken(token);
        return claims == null ? null : claims.get("role", String.class);
    }

    public boolean isValid(String token) {
        return parseToken(token) != null;
    }
}
