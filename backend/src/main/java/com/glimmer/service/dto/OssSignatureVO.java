package com.glimmer.service.dto;

import lombok.Data;

/**
 * OSS PostObject V4 直传签名响应
 * 前端携带这些字段直接 POST 到 OSS host
 */
@Data
public class OssSignatureVO {

    /** OSS 上传地址（https://bucket.endpoint） */
    private String host;

    /** Base64 编码的 policy */
    private String policy;

    /** V4 签名 */
    private String signature;

    /** 签名凭证（AccessKeyId/date/region/oss/aliyun_v4_request） */
    private String xOssCredential;

    /** 签名时间（yyyyMMdd'T'HHmmss'Z'） */
    private String xOssDate;

    /** 签名版本（OSS4-HMAC-SHA256） */
    private String xOssSignatureVersion;

    /** 文件上传目录前缀（如 glimmer/article/） */
    private String dir;

    /** 签名过期时间（Unix 秒） */
    private Long expire;
}
