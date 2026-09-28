-- ============================================================
-- 用户头像系统
-- 1. user.avatar_url：自定义头像URL（NULL = 系统默认头像，按 userId 取默认头像之一）
-- 2. campfire_message.avatar_url：消息发送时的头像快照
--    匿名身份消息存 NULL（前端显示系统默认头像），
--    昵称身份消息存发送者当时的自定义头像（可能为 NULL）。
-- 说明：
--   - 本项目实际由后端 DatabaseMigration 启动时幂等加列，
--     本文件仅为结构存档；但也保证可重复手动执行（幂等）。
--   - MySQL 不支持 ADD COLUMN IF NOT EXISTS（MariaDB 语法），
--     这里通过 information_schema 判断 + PREPARE 动态SQL实现幂等加列。
-- ============================================================

-- user.avatar_url
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'avatar_url'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE user ADD COLUMN avatar_url VARCHAR(500) DEFAULT NULL COMMENT ''自定义头像URL(NULL=系统默认头像)''',
    'SELECT ''user.avatar_url 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- campfire_message.avatar_url
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campfire_message' AND COLUMN_NAME = 'avatar_url'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE campfire_message ADD COLUMN avatar_url VARCHAR(500) DEFAULT NULL COMMENT ''消息头像快照(匿名时NULL显示默认头像)''',
    'SELECT ''campfire_message.avatar_url 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
