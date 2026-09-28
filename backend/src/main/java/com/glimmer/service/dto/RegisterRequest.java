package com.glimmer.service.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求
 * <p>
 * 所有注册均需手机号 + 验证码：phone + code + password
 * 用户名由系统自动生成（phone_后6位），无需前端传入
 */
@Data
public class RegisterRequest {

    /** 密码（必填） */
    @Size(min = 6, max = 50, message = "密码长度需在6-50个字符之间")
    private String password;

    /** 手机号（必填） */
    private String phone;

    /** 短信验证码（必填，scene=register） */
    private String code;
}
