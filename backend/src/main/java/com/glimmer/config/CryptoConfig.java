package com.glimmer.config;

import com.glimmer.common.util.CryptoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 字段加密配置（见开发文档 §3.5）。
 * <p>
 * DATA_ENCRYPTION_KEY 通过 .env 环境变量注入，32 字节 base64 编码。
 * 生成命令：openssl rand -base64 32
 * <p>
 * 未配置时（开发环境）：
 * - CryptoUtil 不创建 Bean
 * - EncryptedFieldTypeHandler 静态字段为 null
 * - 若此时 TypeHandler 被触发会抛 IllegalStateException，提示配置密钥
 * <p>
 * 这意味着：生产环境必须配置密钥，否则启动后第一次读写加密字段会报错。
 */
@Slf4j
@Configuration
public class CryptoConfig {

    @Bean
    public CryptoUtil cryptoUtil(@Value("${glimmer.crypto.data-key:}") String dataKey) {
        CryptoUtil util = new CryptoUtil(dataKey);
        // 注入到静态 TypeHandler
        EncryptedFieldTypeHandler.setCryptoUtil(util);
        log.info("字段加密已启用：AES-256-GCM");
        return util;
    }
}
