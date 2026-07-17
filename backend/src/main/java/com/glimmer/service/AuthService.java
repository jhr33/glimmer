package com.glimmer.service;

import com.glimmer.service.dto.LoginRequest;
import com.glimmer.service.dto.LoginResponse;
import com.glimmer.service.dto.RegisterRequest;

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
}
