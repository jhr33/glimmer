package com.glimmer.controller.api;

import com.glimmer.common.response.Result;
import com.glimmer.common.util.SecurityUtils;
import com.glimmer.config.security.LoginSessionManager;
import com.glimmer.service.AuthService;
import com.glimmer.service.CaptchaService;
import com.glimmer.service.SmsService;
import com.glimmer.service.dto.BindPhoneRequest;
import com.glimmer.service.dto.LoginRequest;
import com.glimmer.service.dto.LoginResponse;
import com.glimmer.service.dto.PhoneLoginRequest;
import com.glimmer.service.dto.RegisterRequest;
import com.glimmer.service.dto.SendSmsCodeRequest;
import com.glimmer.service.dto.SmsLoginRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 鉴权接口（注册、登录、退出）
 * 见开发文档 §4.4
 */
@Tag(name = "鉴权接口", description = "注册、登录、退出")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginSessionManager loginSessionManager;
    private final SmsService smsService;
    private final CaptchaService captchaService;

    public AuthController(AuthService authService, LoginSessionManager loginSessionManager,
                          SmsService smsService, CaptchaService captchaService) {
        this.authService = authService;
        this.loginSessionManager = loginSessionManager;
        this.smsService = smsService;
        this.captchaService = captchaService;
    }

    @Operation(summary = "注册（手机号+验证码+密码，自动生成匿名昵称，返回 userId 与 UID）")
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        Long userId = authService.register(request);
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        // UID = 10000 + id，对外作为账号登录凭证之一
        data.put("uid", 10000L + userId);
        return Result.success(data);
    }

    @Operation(summary = "登录（account=手机号或UID + password，返回 JWT + 用户信息 + UID）")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return Result.success(response);
    }

    @Operation(summary = "退出登录（使当前会话立即失效）")
    @PostMapping("/logout")
    public Result<Void> logout() {
        // 主动退出：删除 Redis 中的会话 jti，token 即刻失效；未登录调用也算成功
        Long userId = SecurityUtils.getCurrentUserIdOrNull();
        if (userId != null) {
            loginSessionManager.invalidate(userId);
        }
        return Result.success(null);
    }

    @Operation(summary = "发送短信验证码（登录/注册/绑定）")
    @PostMapping("/sms-code")
    public Result<Void> sendSmsCode(@Valid @RequestBody SendSmsCodeRequest request) {
        smsService.sendCode(request.getPhone(), request.getScene(),
                request.getCaptchaId(), request.getCaptcha());
        return Result.success(null);
    }

    @Operation(summary = "获取图片验证码（发送短信前的人机校验，防脚本刷短信）")
    @GetMapping("/captcha")
    public Result<Map<String, String>> getCaptcha() {
        String captchaId = captchaService.generateCaptcha();
        String imageBase64 = captchaService.getCaptchaImage(captchaId);
        Map<String, String> data = new HashMap<>();
        data.put("captchaId", captchaId);
        data.put("image", imageBase64);
        return Result.success(data);
    }

    @Operation(summary = "手机号验证码登录（未注册自动建号）")
    @PostMapping("/login-sms")
    public Result<LoginResponse> loginBySms(@Valid @RequestBody SmsLoginRequest request) {
        LoginResponse response = authService.loginBySms(request);
        return Result.success(response);
    }

    @Operation(summary = "手机号密码登录")
    @PostMapping("/login-phone")
    public Result<LoginResponse> loginByPhone(@Valid @RequestBody PhoneLoginRequest request) {
        LoginResponse response = authService.loginByPhone(request);
        return Result.success(response);
    }

    @Operation(summary = "绑定/换绑手机号（需登录）")
    @PostMapping("/bind-phone")
    public Result<Void> bindPhone(@Valid @RequestBody BindPhoneRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        authService.bindPhone(userId, request);
        return Result.success(null);
    }
}
