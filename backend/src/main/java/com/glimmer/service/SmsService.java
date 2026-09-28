package com.glimmer.service;

/**
 * 短信验证码服务
 * <p>
 * 验证码生成、发送（阿里云短信）、校验与限流。
 * Redis key 规划：
 *   glimmer:sms:code:{scene}:{phone}     验证码，TTL 5min
 *   glimmer:sms:interval:{phone}         发送间隔限制，TTL 60s
 *   glimmer:sms:daily:{phone}            当日发送次数，TTL 当日24点
 */
public interface SmsService {

    /** 场景：登录 */
    String SCENE_LOGIN = "login";
    /** 场景：注册 */
    String SCENE_REGISTER = "register";
    /** 场景：绑定/换绑手机号 */
    String SCENE_BIND = "bind";

    /**
     * 发送短信验证码
     *
     * @param phone     手机号（11位）
     * @param scene     场景：login/register/bind
     * @param captchaId 图片验证码ID（人机校验）
     * @param captcha   用户输入的图片验证码
     * @throws com.glimmer.common.exception.BusinessException 验证码错误/发送太频繁/超每日上限/短信服务异常
     */
    void sendCode(String phone, String scene, String captchaId, String captcha);

    /**
     * 校验验证码（校验成功后删除，防止重复使用）
     *
     * @param phone 手机号
     * @param scene 场景
     * @param code  用户输入的验证码
     * @throws com.glimmer.common.exception.BusinessException 验证码错误或已过期
     */
    void verifyCode(String phone, String scene, String code);
}
