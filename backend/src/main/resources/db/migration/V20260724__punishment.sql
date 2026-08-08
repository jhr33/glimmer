-- ==============================================
-- 数据库迁移脚本：处罚单表结构调整
-- 功能：支持精准撤销处罚，解决"多条处罚同时存在时，申诉通过会误解除所有处罚"的问题
-- 版本：V20260724
-- 兼容：MySQL 5.7+（不使用 IF NOT EXISTS 语法）
-- ==============================================

-- ==============================================
-- 一、创建 punishment 表（处罚单表）
-- ==============================================
CREATE TABLE IF NOT EXISTS punishment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '处罚单ID',
    user_id BIGINT NOT NULL COMMENT '被处罚用户ID',
    type VARCHAR(20) NOT NULL COMMENT '处罚类型: WARNING/MUTE_24H/MUTE_7D/BAN',
    reason VARCHAR(500) NOT NULL COMMENT '处罚原因',
    source_type VARCHAR(20) NOT NULL COMMENT '来源: ADMIN(管理员手动)/REPORT(举报通过)/AUTO(系统自动)',
    source_id BIGINT NULL COMMENT '来源ID（如report_id或admin_operation_id）',
    start_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '处罚开始时间',
    end_at DATETIME NULL COMMENT '处罚结束时间（永久封禁为NULL）',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE生效/REVOKED已撤销/EXPIRED已过期',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_status (user_id, status),
    INDEX idx_end_at (end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='处罚单表';

-- ==============================================
-- 二、修改 report 表
-- ==============================================

-- 1. 删除 penalty_type 字段（处罚不再由举报直接产生）
-- MySQL 5.7 不支持 DROP COLUMN IF EXISTS，需先检查列是否存在
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'report' AND COLUMN_NAME = 'penalty_type');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE report DROP COLUMN penalty_type', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 新增 punishment_id 字段（用于关联后续生成的处罚单）
-- MySQL 5.7 不支持 ADD COLUMN IF NOT EXISTS，需先检查列是否存在
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'report' AND COLUMN_NAME = 'punishment_id');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE report ADD COLUMN punishment_id BIGINT NULL COMMENT ''关联处罚单ID'' AFTER reviewed_at', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ==============================================
-- 三、修改 feedback 表
-- ==============================================

-- 新增 punishment_id 字段（用于申诉时关联具体的处罚单）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'feedback' AND COLUMN_NAME = 'punishment_id');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE feedback ADD COLUMN punishment_id BIGINT NULL COMMENT ''关联处罚单ID（申诉时使用，用于精准撤销）'' AFTER report_id', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ==============================================
-- 四、数据迁移：将 user 表中现有的 mute_type 和 mute_end_time 迁移到 punishment 表
-- ==============================================

-- 注意：执行此迁移前请确保 punishment 表已创建

INSERT INTO punishment (user_id, type, reason, source_type, source_id, start_at, end_at, status, created_at, updated_at)
SELECT 
    id AS user_id,
    CASE 
        WHEN mute_type = 'warning' THEN 'WARNING'
        WHEN mute_type = 'mute_24h' THEN 'MUTE_24H'
        WHEN mute_type = 'mute_7d' THEN 'MUTE_7D'
        WHEN mute_type = 'ban' THEN 'BAN'
        ELSE 'WARNING'
    END AS type,
    '数据迁移：从旧版user表迁移的处罚记录' AS reason,
    'AUTO' AS source_type,
    NULL AS source_id,
    CASE 
        WHEN mute_end_time IS NOT NULL THEN DATE_SUB(mute_end_time, INTERVAL 24 HOUR) 
        ELSE created_at 
    END AS start_at,
    mute_end_time AS end_at,
    'ACTIVE' AS status,
    created_at AS created_at,
    updated_at AS updated_at
FROM user 
WHERE mute_type IS NOT NULL;

-- ==============================================
-- 五、修改 user 表（迁移完成后执行）
-- ==============================================

-- 删除 mute_type 字段（用户当前状态改为实时查询 punishment 表计算）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'mute_type');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE user DROP COLUMN mute_type', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 删除 mute_end_time 字段
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS 
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'mute_end_time');
SET @sql = IF(@col_exists > 0, 'ALTER TABLE user DROP COLUMN mute_end_time', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ==============================================
-- 回滚脚本（如需回滚，按相反顺序执行）
-- ==============================================
-- 
-- 1. 恢复 user 表字段（需先恢复数据）
-- ALTER TABLE user ADD COLUMN mute_type VARCHAR(20) NULL COMMENT '封禁类型: warning/mute_24h/mute_7d/ban';
-- ALTER TABLE user ADD COLUMN mute_end_time DATETIME NULL COMMENT '封禁结束时间';
-- 
-- 2. 将 punishment 表数据迁移回 user 表（仅恢复最新的处罚记录）
-- UPDATE user u 
-- JOIN (
--     SELECT user_id, type, end_at 
--     FROM punishment 
--     WHERE status = 'ACTIVE' 
--     ORDER BY created_at DESC 
--     LIMIT 1
-- ) p ON u.id = p.user_id
-- SET 
--     u.mute_type = CASE 
--         WHEN p.type = 'WARNING' THEN 'warning'
--         WHEN p.type = 'MUTE_24H' THEN 'mute_24h'
--         WHEN p.type = 'MUTE_7D' THEN 'mute_7d'
--         WHEN p.type = 'BAN' THEN 'ban'
--         ELSE p.type
--     END,
--     u.mute_end_time = p.end_at;
-- 
-- 3. 删除 punishment 表
-- DROP TABLE IF EXISTS punishment;
-- 
-- 4. 恢复 report 表字段
-- ALTER TABLE report ADD COLUMN penalty_type VARCHAR(20) NULL COMMENT '处罚类型: warning/mute_24h/mute_7d/ban';
-- ALTER TABLE report DROP COLUMN punishment_id;
-- 
-- 5. 恢复 feedback 表
-- ALTER TABLE feedback DROP COLUMN punishment_id;
