package com.glimmer.service;

import com.glimmer.service.dto.OssSignatureVO;

/**
 * OSS 对象存储服务
 * <p>
 * 采用前端直传方案：后端仅签发 PostObject V4 签名，前端携带签名直传 OSS，
 * 服务器不过文件流量，减轻带宽压力。
 */
public interface OssService {

    /**
     * 生成 OSS PostObject V4 直传签名
     *
     * @param dir 上传子目录（如 article / campfire），拼在 dirPrefix 之后
     * @return 签名信息（host/policy/signature 等）
     */
    OssSignatureVO generatePostSignature(String dir);
}
