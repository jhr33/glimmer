package com.glimmer.service;

import com.glimmer.service.dto.BindPhoneRequest;
import com.glimmer.service.dto.LoginRequest;
import com.glimmer.service.dto.LoginResponse;
import com.glimmer.service.dto.PhoneLoginRequest;
import com.glimmer.service.dto.RegisterRequest;
import com.glimmer.service.dto.SmsLoginRequest;

/**
 * 鉴权服务（注册、登录）
 */
public interface AuthService {

    /**
     * 注册：用户名+密码，自动生成匿名昵称
     *
     * @return 新用户ID
     */
    Long register(RegisterRequest request);

    /**
     * 登录：返回 JWT + 用户信息
     */
    LoginResponse login(LoginRequest request);

    /**
     * 手机号验证码登录：未注册则自动建号
     */
    LoginResponse loginBySms(SmsLoginRequest request);

    /**
     * 手机号密码登录
     */
    LoginResponse loginByPhone(PhoneLoginRequest request);

    /**
     * 绑定/换绑手机号（需登录）
     */
    void bindPhone(Long userId, BindPhoneRequest request);
}
