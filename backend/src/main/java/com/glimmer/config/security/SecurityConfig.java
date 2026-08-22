package com.glimmer.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.glimmer.common.response.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import jakarta.servlet.DispatcherType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Spring Security 配置（见开发文档 §4.3）
 * - 白名单：注册、登录、公告、漂流瓶列表、Swagger
 * - /api/admin/** 需 ADMIN 角色
 * - 其余接口需登录
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // ASYNC 分派放行：SseEmitter 完成时触发异步分派，
                // JwtAuthenticationFilter（OncePerRequestFilter）默认不处理 ASYNC 分派 → SecurityContext 为空
                // → AuthorizationFilter 抛 AccessDeniedException → ExceptionTranslationFilter 也跳过 ASYNC → 异常传播到容器
                .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                // 错误页必须放行：Tomcat 异常/404 会 forward 到 /error，
                // 若不放行会触发 "Unable to handle the Spring Security Exception because the response is already committed"
                .requestMatchers("/error").permitAll()
                // 白名单接口（见开发文档 §2.1.3 / §4.3）
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/announcements", "/api/announcements/**").permitAll()
                // 公共只读接口：游客可浏览（所有写操作仍需登录）
                // —— 小篝火：列表 / 详情 / 历史消息 ——
                .requestMatchers("GET", "/api/campfires").permitAll()
                .requestMatchers("GET", "/api/campfires/{campfireId}").permitAll()
                .requestMatchers("GET", "/api/campfires/{campfireId}/messages").permitAll()
                // —— 漂流瓶：公海列表 / 详情 / 回复列表 / 放回（捡瓶/投瓶/回复/感谢仍是 POST，不放行） ——
                .requestMatchers("GET", "/api/bottles").permitAll()
                .requestMatchers("GET", "/api/bottles/{bottleId}").permitAll()
                .requestMatchers("GET", "/api/bottles/{bottleId}/replies").permitAll()
                .requestMatchers("POST", "/api/bottles/{bottleId}/release").permitAll()
                // —— 萤火花园：花类型 / 花朵列表 / 花朵详情（浇水 / 兑换仍是 POST，不放行） ——
                .requestMatchers("GET", "/api/flower-types").permitAll()
                .requestMatchers("GET", "/api/flowers").permitAll()
                .requestMatchers("GET", "/api/flowers/{flowerId}").permitAll()
                // —— 用户公开信息：他人花园页 / 公开资料页 ——
                .requestMatchers("GET", "/api/users/{userId}/garden").permitAll()
                .requestMatchers("GET", "/api/users/{userId}/profile").permitAll()
                // WebSocket 端点（鉴权由 JwtHandshakeInterceptor 处理，见 §3.4.3）
                .requestMatchers("/ws-campfire/**").permitAll()
                // 接口文档
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/swagger-ui.html").permitAll()
                // 管理员接口
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // 其余接口需登录
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                        writeJson(response, 401, "未登录或token失效"))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                        writeJson(response, 403, "无权限"))
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private void writeJson(HttpServletResponse response, int code, String message) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(code, message)));
    }
}
