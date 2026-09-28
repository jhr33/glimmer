package com.glimmer.service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求
 * <p>
 * account 支持两种形式：
 * - 11 位手机号（1[3-9] 开头）→ 按 phone 查询
 * - 纯数字 UID → 反推 id = uid - 10000 后按主键查询
 * 用户名登录已下线，username 不再作为登录凭证。
 */
@Data
public class LoginRequest {

    /** 账号：手机号或 UID */
    @NotBlank(message = "账号不能为空")
    private String account;

    @NotBlank(message = "密码不能为空")
    private String password;
}
