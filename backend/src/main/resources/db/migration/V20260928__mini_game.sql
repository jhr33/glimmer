-- ============================================================
-- 小游戏板块：游戏成绩排行榜
-- 1. game_score 记录每局游戏成绩（首期仅贪吃蛇 snake，game_type 预留扩展）
-- 2. display_name 为成绩产生时的昵称快照，排行榜查询无需关联 user 表
-- 3. 排行榜按"每个用户只保留最高分"聚合：
--    idx_game_type_score 支撑 WHERE game_type=? ORDER BY score DESC 扫描
-- 说明：
--   - 本项目实际由后端 DatabaseMigration 启动时幂等建表，
--     本文件仅为结构存档；但也保证可重复手动执行（幂等）。
-- ============================================================

CREATE TABLE IF NOT EXISTS game_score (
    id               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '成绩ID',
    user_id          BIGINT      NOT NULL COMMENT '玩家用户ID',
    game_type        VARCHAR(32) NOT NULL DEFAULT 'snake' COMMENT '游戏类型: snake贪吃蛇（预留扩展）',
    display_name     VARCHAR(50) NOT NULL COMMENT '成绩产生时的昵称快照',
    score            INT         NOT NULL DEFAULT 0 COMMENT '本局得分',
    level            INT         NOT NULL DEFAULT 1 COMMENT '本局达到的难度等级',
    duration_seconds INT         NOT NULL DEFAULT 0 COMMENT '本局时长（秒）',
    created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_game_type_score (game_type, score, created_at),
    KEY idx_user (user_id, game_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小游戏成绩表';
