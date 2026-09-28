package com.glimmer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云短信服务配置
 * <p>
 * 敏感信息（accessKeyId/accessKeySecret）通过环境变量注入，绝不硬编码：
 * ALIYUN_SMS_ACCESS_KEY_ID / ALIYUN_SMS_ACCESS_KEY_SECRET
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "glimmer.aliyun.sms")
public class AliyunSmsConfig {

    /** 号码认证服务接入点（dypnsapi，非普通短信 dysmsapi） */
    private String endpoint = "dypnsapi.aliyuncs.com";

    /** AccessKey ID（环境变量注入） */
    private String accessKeyId;

    /** AccessKey Secret（环境变量注入） */
    private String accessKeySecret;

    /** 短信签名名称 */
    private String signName;

    /** 短信模板 Code */
    private String templateCode;

    /** 验证码有效期（分钟），与模板文案 ${min} 对应 */
    private Integer codeExpireMinutes = 5;

    /** 同一手机号发送间隔（秒） */
    private Integer sendIntervalSeconds = 60;

    /** 同一手机号每日发送上限 */
    private Integer dailyLimit = 10;
}
