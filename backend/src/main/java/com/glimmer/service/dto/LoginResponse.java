package com.glimmer.service.dto;

import lombok.Data;

/**
 * 登录响应（JWT + 用户信息）
 */
@Data
public class LoginResponse {

    private String token;
    private UserVO user;

    /**
     * 是否已设置昵称（false 表示首次登录需要填写 nickname）
     */
    private Boolean nicknameSet;
}
