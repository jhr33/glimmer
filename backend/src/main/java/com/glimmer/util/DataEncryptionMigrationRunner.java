package com.glimmer.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.glimmer.common.util.CryptoUtil;
import com.glimmer.entity.AiMessage;
import com.glimmer.entity.Letter;
import com.glimmer.mapper.AiMessageMapper;
import com.glimmer.mapper.LetterMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 一次性数据迁移工具：将历史明文 content 加密为 AES-GCM 密文。
 * <p>
 * 触发方式：启动时设置 JVM 参数 -Dglimmer.migrate-encrypt=true
 *   例：java -Dglimmer.migrate-encrypt=true -jar glimmer-backend.jar
 * <p>
 * 不带该参数则不执行，正常启动。
 * 迁移逻辑：
 *   - 查询所有 content 不以 "ENC:" 开头的记录
 *   - 调用 CryptoUtil.encrypt 加密
 *   - 直接 UPDATE 回写（绕过 TypeHandler，避免重复加密）
 * <p>
 * 幂等：已加密记录跳过，可重复执行。
 * 注意：此 Runner 在生产环境只应执行一次，迁移完成后可删除此类。
 */
@Slf4j
@Component
public class DataEncryptionMigrationRunner implements CommandLineRunner {

    /** 启用迁移的 JVM 参数名 */
    private static final String MIGRATE_FLAG = "glimmer.migrate-encrypt";
    /** 加密前缀，与 CryptoUtil 中保持一致 */
    private static final String ENCRYPTED_PREFIX = "ENC:";
    /** 单批次处理数量，避免大表一次性加载 OOM */
    private static final int BATCH_SIZE = 500;

    @Autowired
    private AiMessageMapper aiMessageMapper;

    @Autowired
    private LetterMapper letterMapper;

    @Autowired
    private CryptoUtil cryptoUtil;

    @Override
    public void run(String... args) {
        String flag = System.getProperty(MIGRATE_FLAG);
        if (!"true".equalsIgnoreCase(flag)) {
            return;
        }

        log.warn("==== 数据加密迁移开始（-D{}=true）====", MIGRATE_FLAG);
        log.warn("仅迁移 ai_message / letter（篝火消息明文存储，不迁移）");
        int aiTotal = migrateAiMessage();
        int letterTotal = migrateLetter();
        log.warn("==== 数据加密迁移完成：ai_message={} 条, letter={} 条 ====", aiTotal, letterTotal);
    }

    private int migrateAiMessage() {
        long startTime = System.currentTimeMillis();
        int migrated = 0;
        long lastId = 0L;

        while (true) {
            // 分批查询未加密记录
            List<AiMessage> batch = aiMessageMapper.selectList(
                    new LambdaQueryWrapper<AiMessage>()
                            .gt(AiMessage::getId, lastId)
                            .and(w -> w.notLike(AiMessage::getContent, "ENC:%"))
                            .orderByAsc(AiMessage::getId)
                            .last("LIMIT " + BATCH_SIZE));

            if (batch.isEmpty()) {
                break;
            }

            for (AiMessage msg : batch) {
                String plaintext = msg.getContent();
                if (plaintext == null || plaintext.isEmpty() || plaintext.startsWith(ENCRYPTED_PREFIX)) {
                    lastId = msg.getId();
                    continue;
                }
                String encrypted = cryptoUtil.encrypt(plaintext);
                aiMessageMapper.update(null, new LambdaUpdateWrapper<AiMessage>()
                        .eq(AiMessage::getId, msg.getId())
                        .set(AiMessage::getContent, encrypted));
                migrated++;
                lastId = msg.getId();
            }

            log.info("ai_message 已处理到 id={}, 累计迁移={}", lastId, migrated);
        }

        long cost = System.currentTimeMillis() - startTime;
        log.info("ai_message 迁移完成：共 {} 条, 耗时 {}ms", migrated, cost);
        return migrated;
    }

    private int migrateLetter() {
        long startTime = System.currentTimeMillis();
        int migrated = 0;
        long lastId = 0L;

        while (true) {
            List<Letter> batch = letterMapper.selectList(
                    new LambdaQueryWrapper<Letter>()
                            .gt(Letter::getId, lastId)
                            .and(w -> w.notLike(Letter::getContent, "ENC:%"))
                            .orderByAsc(Letter::getId)
                            .last("LIMIT " + BATCH_SIZE));

            if (batch.isEmpty()) {
                break;
            }

            for (Letter letter : batch) {
                String plaintext = letter.getContent();
                if (plaintext == null || plaintext.isEmpty() || plaintext.startsWith(ENCRYPTED_PREFIX)) {
                    lastId = letter.getId();
                    continue;
                }
                String encrypted = cryptoUtil.encrypt(plaintext);
                letterMapper.update(null, new LambdaUpdateWrapper<Letter>()
                        .eq(Letter::getId, letter.getId())
                        .set(Letter::getContent, encrypted));
                migrated++;
                lastId = letter.getId();
            }

            log.info("letter 已处理到 id={}, 累计迁移={}", lastId, migrated);
        }

        long cost = System.currentTimeMillis() - startTime;
        log.info("letter 迁移完成：共 {} 条, 耗时 {}ms", migrated, cost);
        return migrated;
    }
}
