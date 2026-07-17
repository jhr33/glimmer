package com.glimmer.service.dto;

import lombok.Data;

/**
 * 登录响应（JWT + 用户信息）
 */
@Data
public class LoginResponse {

    private String token;
    private UserVO user;
}
