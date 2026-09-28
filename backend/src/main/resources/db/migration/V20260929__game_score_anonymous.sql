-- ============================================================
-- 小游戏排行榜：匿名上榜支持
-- 1. game_score 增加 is_anonymous 标志：玩家自选该成绩是否匿名展示
-- 2. game_score 增加 anonymous_name：匿名昵称快照（提交时从平台统一
--    匿名昵称复制，24小时轮换逻辑在 user 表，此处只做快照，仅供排行榜使用）
-- 说明：幂等写法，新库/老库重复执行均安全（兼容 MySQL 5.7/8.0）
-- ============================================================

-- 匿名标志：0=实名（展示 display_name 昵称快照），1=匿名（展示 anonymous_name）
SET @ddl = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'game_score'
           AND COLUMN_NAME = 'is_anonymous') = 0,
        'ALTER TABLE game_score ADD COLUMN is_anonymous TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''排行榜是否匿名: 0否 1是''',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 匿名昵称快照：仅 is_anonymous=1 时有值，仅用于排行榜展示
SET @ddl = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'game_score'
           AND COLUMN_NAME = 'anonymous_name') = 0,
        'ALTER TABLE game_score ADD COLUMN anonymous_name VARCHAR(50) DEFAULT NULL COMMENT ''匿名昵称快照（仅排行榜展示）''',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
