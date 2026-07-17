package com.glimmer.controller.api;

import com.glimmer.common.response.Result;
import com.glimmer.service.AuthService;
import com.glimmer.service.dto.LoginRequest;
import com.glimmer.service.dto.LoginResponse;
import com.glimmer.service.dto.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 鉴权接口（注册、登录）
 * 见开发文档 §4.4
 */
@Tag(name = "鉴权接口", description = "注册、登录")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "注册（用户名+密码，自动生成匿名昵称）")
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        Long userId = authService.register(request);
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        return Result.success(data);
    }

    @Operation(summary = "登录（返回 JWT + 用户信息）")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return Result.success(response);
    }
}
