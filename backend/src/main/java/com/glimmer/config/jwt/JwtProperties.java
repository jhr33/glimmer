package com.glimmer.config.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置属性
 * glimmer.jwt.secret / glimmer.jwt.expiration
 */
@Data
@Component
@ConfigurationProperties(prefix = "glimmer.jwt")
public class JwtProperties {

    /** JWT 密钥，建议通过环境变量 JWT_SECRET 注入 */
    private String secret;

    /** 过期时间（毫秒），默认 30 天，配合前端 localStorage 长期免登录 */
    private long expiration = 2592000000L;
}
