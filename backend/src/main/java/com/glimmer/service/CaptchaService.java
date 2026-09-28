package com.glimmer.service;

/**
 * 图片验证码服务
 * <p>
 * 生成图形验证码，存储到 Redis（key=glimmer:captcha:{captchaId}, value=code, TTL=2min），
 * 发送短信前校验，防止脚本刷短信。
 */
public interface CaptchaService {

    /**
     * 生成图片验证码
     *
     * @return 验证码ID（用于校验时回传）
     */
    String generateCaptcha();

    /**
     * 获取验证码图片 Base64
     *
     * @param captchaId 验证码ID
     * @return base64 编码的 PNG 图片
     */
    String getCaptchaImage(String captchaId);

    /**
     * 校验验证码（校验成功后删除，防止重复使用）
     *
     * @param captchaId 验证码ID
     * @param code      用户输入的验证码
     * @return true=校验通过
     */
    boolean verifyCaptcha(String captchaId, String code);
}
