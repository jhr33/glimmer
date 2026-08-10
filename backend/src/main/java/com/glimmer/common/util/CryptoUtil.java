package com.glimmer.common.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM 加解密工具（应用层字段级加密，见开发文档 §3.5）。
 * <p>
 * 设计要点：
 * - AES-256-GCM：自带完整性校验（Auth Tag），防篡改
 * - 每条消息独立 IV（12 字节随机），IV 与密文一同存储（IV 公开无风险，只要不重复即可）
 * - 存储格式：base64(IV || 密文 || Tag) —— 单字段 text 可承载
 * - 密钥来自环境变量 DATA_ENCRYPTION_KEY（32 字节 base64 编码），不在 DB、不进 Git
 * <p>
 * 容错：明文短串（未被加密的历史数据）会原样返回，便于灰度迁移。
 */
public class CryptoUtil {

    /** AES-GCM IV 长度（字节），NIST 推荐 12 */
    private static final int IV_LENGTH = 12;
    /** GCM 认证标签长度（比特） */
    private static final int TAG_LENGTH_BITS = 128;
    /** 加密结果前缀，用于识别已加密数据（避免对明文重复加密） */
    private static final String ENCRYPTED_PREFIX = "ENC:";
    /** 256-bit 密钥的字节长度 */
    private static final int KEY_LENGTH_BYTES = 32;

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public CryptoUtil(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                "DATA_ENCRYPTION_KEY 未配置。请在 .env 设置 32 字节 base64 编码的密钥（可用命令生成：openssl rand -base64 32）");
        }
        byte[] keyBytes = Base64.getDecoder().decode(base64Key.trim());
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                "DATA_ENCRYPTION_KEY 必须是 32 字节（256 位）的 base64 编码，当前: " + keyBytes.length + " 字节");
        }
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * 加密明文，返回 base64(IV || 密文 || Tag)，带 ENC: 前缀。
     * 已加密（带前缀）的输入原样返回，避免重复加密。
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        // 已加密直接返回（幂等）
        if (plaintext.startsWith(ENCRYPTED_PREFIX)) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 拼接 IV || cipherText（GCM 的 cipherText 末尾已包含 Tag）
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    /**
     * 解密。输入必须是 encrypt() 产生的带 ENC: 前缀的字符串。
     * 不带前缀（明文历史数据）原样返回，便于灰度迁移。
     */
    public String decrypt(String stored) {
        if (stored == null || stored.isEmpty()) {
            return stored;
        }
        // 明文历史数据原样返回（灰度兼容）
        if (!stored.startsWith(ENCRYPTED_PREFIX)) {
            return stored;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(ENCRYPTED_PREFIX.length()));

            if (combined.length <= IV_LENGTH) {
                // 数据异常，返回原值避免业务中断
                return stored;
            }

            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherText = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 解密失败不抛异常到业务层，返回原值 + 日志（由调用方记日志）
            // 这里返回 null 标记失败，调用方判断
            return null;
        }
    }
}
