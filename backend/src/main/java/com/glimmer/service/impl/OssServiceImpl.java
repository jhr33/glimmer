package com.glimmer.service.impl;

import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.config.AliyunOssConfig;
import com.glimmer.service.OssService;
import com.glimmer.service.dto.OssSignatureVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * OSS 服务实现
 * <p>
 * PostObject V4 签名流程：
 * 1. 构建 policy（含 expiration、bucket、dir、content-length-range 等条件）
 * 2. Base64 编码 policy
 * 3. 用 HmacSHA256 派生签名密钥（dateSecret → dateRegionSecret → dateRegionServiceSecret → signingKey）
 * 4. signingKey 对 Base64(policy) 签名得到 signature
 * 5. 前端携带 policy + signature + x-oss-credential 等字段直传 OSS
 *
 * 参考：https://help.aliyun.com/zh/oss/developer-reference/postobject
 */
@Slf4j
@Service
public class OssServiceImpl implements OssService {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String PRODUCT = "oss";
    private static final String REQUEST_TYPE = "aliyun_v4_request";
    private static final String SIGNATURE_VERSION = "OSS4-HMAC-SHA256";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final AliyunOssConfig ossConfig;

    public OssServiceImpl(AliyunOssConfig ossConfig) {
        this.ossConfig = ossConfig;
    }

    @Override
    public OssSignatureVO generatePostSignature(String dir) {
        // 1. 校验配置
        if (ossConfig.getAccessKeyId() == null || ossConfig.getAccessKeySecret() == null
                || ossConfig.getEndpoint() == null || ossConfig.getBucketName() == null) {
            throw new BusinessException(ErrorCode.OSS_NOT_CONFIGURED);
        }

        String fullDir = ossConfig.getDirPrefix() + dir + "/";
        long maxFileSizeBytes = (long) ossConfig.getMaxFileSizeMb() * 1024 * 1024;
        long expireTimeMillis = System.currentTimeMillis()
                + (long) ossConfig.getSignatureExpireMinutes() * 60 * 1000;

        // 2. 构建 policy
        Instant now = Instant.now();
        Instant expiration = Instant.ofEpochMilli(expireTimeMillis);
        String dateStr = ZonedDateTime.ofInstant(now, ZoneOffset.UTC).format(DATE_FORMAT);
        String dateTimeStr = ZonedDateTime.ofInstant(now, ZoneOffset.UTC).format(DATE_TIME_FORMAT);
        String region = ossConfig.resolveRegion();

        String credential = String.format("%s/%s/%s/%s/%s",
                ossConfig.getAccessKeyId(), dateStr, region, PRODUCT, REQUEST_TYPE);

        // policy JSON：expiration + conditions（bucket、x-oss-* 字段、文件大小、目录前缀）
        String policyJson = String.format(
                "{\"expiration\":\"%s\",\"conditions\":[" +
                        "{\"bucket\":\"%s\"}," +
                        "{\"x-oss-signature-version\":\"%s\"}," +
                        "{\"x-oss-credential\":\"%s\"}," +
                        "{\"x-oss-date\":\"%s\"}," +
                        "[\"content-length-range\",1,%d]," +
                        "[\"starts-with\",\"$key\",\"%s\"]," +
                        "[\"in\",\"$content-type\",[\"image/jpeg\",\"image/png\",\"image/gif\",\"image/webp\"]]" +
                        "]}",
                expiration.toString(),
                ossConfig.getBucketName(),
                SIGNATURE_VERSION,
                credential,
                dateTimeStr,
                maxFileSizeBytes,
                fullDir
        );

        // 3. Base64 编码 policy
        String policyBase64 = Base64.getEncoder().encodeToString(policyJson.getBytes(StandardCharsets.UTF_8));

        // 4. V4 签名：HmacSHA256 派生签名密钥
        try {
            byte[] dateSecret = hmacSha256(("aliyun_v4" + ossConfig.getAccessKeySecret()).getBytes(StandardCharsets.UTF_8),
                    dateStr.getBytes(StandardCharsets.UTF_8));
            byte[] dateRegionSecret = hmacSha256(dateSecret, region.getBytes(StandardCharsets.UTF_8));
            byte[] dateRegionServiceSecret = hmacSha256(dateRegionSecret, PRODUCT.getBytes(StandardCharsets.UTF_8));
            byte[] signingKey = hmacSha256(dateRegionServiceSecret, REQUEST_TYPE.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = hmacSha256(signingKey, policyBase64.getBytes(StandardCharsets.UTF_8));
            String signature = bytesToHex(signatureBytes);

            // 5. 构建返回 VO
            OssSignatureVO vo = new OssSignatureVO();
            vo.setHost(String.format("https://%s.%s", ossConfig.getBucketName(), ossConfig.getEndpoint()));
            vo.setPolicy(policyBase64);
            vo.setSignature(signature);
            vo.setXOssCredential(credential);
            vo.setXOssDate(dateTimeStr);
            vo.setXOssSignatureVersion(SIGNATURE_VERSION);
            vo.setDir(fullDir);
            vo.setExpire(expireTimeMillis / 1000);
            return vo;
        } catch (Exception e) {
            log.error("OSS签名生成失败: error={}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.OSS_SIGNATURE_FAILED);
        }
    }

    private byte[] hmacSha256(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance(ALGORITHM);
        mac.init(new SecretKeySpec(key, ALGORITHM));
        return mac.doFinal(data);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
