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

    /**
     * 内地函数计算（FC）中转地址，如 https://xxx.cn-hangzhou.fcapp.run
     * 背景：香港服务器到 dypnsapi（106.11.x）网络不通，生产环境经 FC 中转。
     * 留空则直连（本地开发/内地网络环境使用）。
     */
    private String relayUrl;

    /** FC 中转共享密钥（与 FC 环境变量 RELAY_SECRET 一致，防公网滥用） */
    private String relaySecret;

    /** 验证码有效期（分钟），与模板文案 ${min} 对应 */
    private Integer codeExpireMinutes = 5;

    /** 同一手机号发送间隔（秒） */
    private Integer sendIntervalSeconds = 60;

    /** 同一手机号每日发送上限 */
    private Integer dailyLimit = 10;
}
