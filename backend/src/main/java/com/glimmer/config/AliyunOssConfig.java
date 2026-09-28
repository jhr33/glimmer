package com.glimmer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 配置
 * <p>
 * 敏感信息（accessKeyId/accessKeySecret）通过环境变量注入，绝不硬编码：
 * ALIYUN_OSS_ACCESS_KEY_ID / ALIYUN_OSS_ACCESS_KEY_SECRET
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "glimmer.aliyun.oss")
public class AliyunOssConfig {

    /** OSS 地域节点，如 oss-cn-hangzhou.aliyuncs.com */
    private String endpoint;

    /** Bucket 名称 */
    private String bucketName;

    /** AccessKey ID（环境变量注入） */
    private String accessKeyId;

    /** AccessKey Secret（环境变量注入） */
    private String accessKeySecret;

    /** 上传目录前缀 */
    private String dirPrefix = "glimmer/";

    /** 签名有效期（分钟） */
    private Integer signatureExpireMinutes = 10;

    /** 单文件大小上限（MB） */
    private Integer maxFileSizeMb = 5;

    /**
     * 从 endpoint 解析 region：oss-cn-hangzhou.aliyuncs.com → cn-hangzhou
     */
    public String resolveRegion() {
        if (endpoint == null) {
            return "cn-hangzhou";
        }
        String host = endpoint.replace("https://", "").replace("http://", "");
        // 去掉 "oss-" 前缀和 ".aliyuncs.com" 后缀
        if (host.startsWith("oss-") && host.endsWith(".aliyuncs.com")) {
            return host.substring(4, host.length() - ".aliyuncs.com".length());
        }
        return host;
    }
}
