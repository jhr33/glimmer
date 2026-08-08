package com.glimmer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 轻量级数据库 Schema 迁移
 * 仅在需要时执行 ALTER TABLE，幂等安全
 */
@Slf4j
@Component
public class DatabaseMigration implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        addColumnIfNotExists(
                "campfire_member", "anonymous_name",
                "VARCHAR(100) DEFAULT NULL COMMENT '篝火内身份名称'",
                "campfire_member.anonymous_name"
        );
        // AI 对话重构：配额、摘要、会话类型
        addColumnIfNotExists("ai_conversation", "conversation_type",
                "VARCHAR(10) DEFAULT 'paid' NOT NULL COMMENT '类型: free免费/paid付费'",
                "ai_conversation.conversation_type");
        addColumnIfNotExists("ai_conversation", "quota_used",
                "INT DEFAULT 0 NOT NULL COMMENT '已用轮次'",
                "ai_conversation.quota_used");
        addColumnIfNotExists("ai_conversation", "quota_limit",
                "INT DEFAULT 10 NOT NULL COMMENT '轮次上限'",
                "ai_conversation.quota_limit");
        addColumnIfNotExists("ai_conversation", "quota_reset_date",
                "DATE DEFAULT NULL COMMENT '配额重置日期(仅free)'",
                "ai_conversation.quota_reset_date");
        addColumnIfNotExists("ai_conversation", "summary",
                "TEXT DEFAULT NULL COMMENT '会话摘要'",
                "ai_conversation.summary");
        addColumnIfNotExists("ai_conversation", "title",
                "VARCHAR(50) DEFAULT NULL COMMENT '会话标题(首条消息摘要)'",
                "ai_conversation.title");
        // 用户表：AI 记忆关键信息
        addColumnIfNotExists("user", "ai_context",
                "JSON DEFAULT NULL COMMENT 'AI记忆关键信息JSON'",
                "user.ai_context");
    }

    private void addColumnIfNotExists(String table, String column, String definition, String label) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    Integer.class, table, column
            );
            if (count != null && count > 0) {
                log.info("[DB迁移] 列已存在，跳过: {}", label);
                return;
            }
            String sql = String.format("ALTER TABLE %s ADD COLUMN %s %s", table, column, definition);
            jdbcTemplate.execute(sql);
            log.info("[DB迁移] 成功添加列: {}", label);
        } catch (Exception e) {
            log.error("[DB迁移] 添加列失败: {}, error={}", label, e.getMessage());
        }
    }
}
