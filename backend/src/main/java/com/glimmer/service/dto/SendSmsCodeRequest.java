package com.glimmer.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 发送短信验证码请求
 */
@Data
public class SendSmsCodeRequest {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 场景：login登录 / register注册 / bind绑定 */
    @NotBlank(message = "场景不能为空")
    private String scene;

    /** 图片验证码ID（获取验证码图片时后端返回） */
    @NotBlank(message = "请先完成图片验证")
    private String captchaId;

    /** 用户输入的图片验证码 */
    @NotBlank(message = "请输入图片验证码")
    private String captcha;
}

