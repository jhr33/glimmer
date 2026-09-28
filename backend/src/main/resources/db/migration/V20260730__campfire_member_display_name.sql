-- 篝火成员表增加 anonymous_name 字段（幂等）
-- 存储用户进入篝火时选择的身份名称（匿名模式随机生成，昵称模式按 userId 查询填入）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campfire_member' AND COLUMN_NAME = 'anonymous_name');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE campfire_member ADD COLUMN anonymous_name VARCHAR(100) DEFAULT NULL COMMENT ''篝火内身份名称（昵称或随机匿名）''', 'SELECT ''campfire_member.anonymous_name 已存在，跳过'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
