-- ============================================================
-- 小游戏排行榜：玩家自定义匿名代号
-- game_player_alias 存放"用户 × 游戏"维度的手动匿名代号，
-- 与平台统一匿名昵称（user.anonymous_name，篝火/漂流瓶使用）相互独立，
-- 仅用于对应游戏排行榜；提交成绩时把代号快照进 game_score.anonymous_name。
-- 说明：幂等写法，新库/老库重复执行均安全（兼容 MySQL 5.7/8.0）
-- ============================================================

CREATE TABLE IF NOT EXISTS game_player_alias (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id    BIGINT      NOT NULL COMMENT '玩家用户ID',
    game_type  VARCHAR(32) NOT NULL COMMENT '游戏类型: snake贪吃蛇（预留扩展）',
    alias      VARCHAR(20) NOT NULL COMMENT '玩家手动输入的匿名代号（仅本游戏排行榜使用）',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_game (user_id, game_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小游戏玩家匿名代号表';
