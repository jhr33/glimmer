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

    /**
     * 用户 UID（对外账号，= 10000 + user.id，不入库，运行时计算）
     * 用于登录/注册成功后回显给前端，作为账号登录凭证之一。
     */
    private Long uid;
}
