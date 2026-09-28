-- ============================================================
-- 手机号注册登录 + 图片上传（文章/篝火图片消息）
-- 1. user.phone：手机号，唯一索引（MySQL 唯一索引允许多个 NULL，不冲突）
-- 2. firefly_article.images：图片URL列表，JSON数组字符串，最多 9 张
-- 3. campfire_message.msg_type / image_url：支持图片消息
-- 说明：
--   - 本项目实际由后端 DatabaseMigration 启动时幂等加列/加索引，
--     本文件仅为结构存档；但也保证可重复手动执行（幂等）。
--   - MySQL（含8.0）不支持 ADD COLUMN IF NOT EXISTS（MariaDB 语法），
--     这里通过 information_schema 判断 + PREPARE 动态SQL实现幂等加列。
-- ============================================================

-- user.phone
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'phone'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE user ADD COLUMN phone VARCHAR(11) DEFAULT NULL COMMENT ''手机号''',
    'SELECT ''user.phone 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- user.uk_phone 唯一索引（幂等：通过 STATISTICS 判断）
SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND INDEX_NAME = 'uk_phone'
);
SET @ddl = IF(@idx_exists = 0,
    'ALTER TABLE user ADD UNIQUE KEY uk_phone (phone)',
    'SELECT ''user.uk_phone 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- firefly_article.images
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'firefly_article' AND COLUMN_NAME = 'images'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE firefly_article ADD COLUMN images VARCHAR(2000) DEFAULT NULL COMMENT ''图片URL列表(JSON数组，最多9张)''',
    'SELECT ''firefly_article.images 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- campfire_message.msg_type
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campfire_message' AND COLUMN_NAME = 'msg_type'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE campfire_message ADD COLUMN msg_type VARCHAR(16) NOT NULL DEFAULT ''text'' COMMENT ''消息类型: text文本/image图片''',
    'SELECT ''campfire_message.msg_type 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- campfire_message.image_url
SET @col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campfire_message' AND COLUMN_NAME = 'image_url'
);
SET @ddl = IF(@col_exists = 0,
    'ALTER TABLE campfire_message ADD COLUMN image_url VARCHAR(500) DEFAULT NULL COMMENT ''图片消息URL''',
    'SELECT ''campfire_message.image_url 已存在，跳过'' AS msg');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
