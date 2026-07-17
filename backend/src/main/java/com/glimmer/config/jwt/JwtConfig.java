package com.glimmer.config.jwt;

import com.glimmer.common.util.JwtUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JWT Bean 配置
 */
@Configuration
public class JwtConfig {

    @Bean
    public JwtUtils jwtUtils(JwtProperties jwtProperties) {
        return new JwtUtils(jwtProperties.getSecret(), jwtProperties.getExpiration());
    }
}
