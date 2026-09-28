-- 篝火消息表增加引用回复字段（幂等）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campfire_message' AND COLUMN_NAME = 'quoted_message_id');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE campfire_message ADD COLUMN quoted_message_id BIGINT DEFAULT NULL COMMENT ''被引用的消息ID''', 'SELECT ''campfire_message.quoted_message_id 已存在，跳过'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campfire_message' AND COLUMN_NAME = 'quoted_content');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE campfire_message ADD COLUMN quoted_content VARCHAR(500) DEFAULT NULL COMMENT ''被引用的消息内容（冗余存储）''', 'SELECT ''campfire_message.quoted_content 已存在，跳过'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
