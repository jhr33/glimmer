package com.glimmer.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 绑定/换绑手机号请求
 * <p>
 * force=true：当目标手机号已被其他账户占用时，先把旧账户的 phone 置 NULL 再绑到当前账户。
 * 安全依据：调用方必须先通过 smsCode 校验，证明对新手机号的所有权，因此可视为合法换绑。
 */
@Data
public class BindPhoneRequest {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "验证码不能为空")
    private String code;

    /** 是否强制解绑旧账户（默认 false，由前端在用户确认"解绑并转移"后置 true） */
    private Boolean force = false;
}
