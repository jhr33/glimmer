package com.glimmer.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小游戏玩家匿名代号表（game_player_alias）
 * 一个用户在每个游戏下最多一个手动代号（uk_user_game 唯一约束），
 * 与平台统一匿名昵称相互独立，仅用于对应游戏的排行榜展示
 */
@Data
@TableName("game_player_alias")
public class GamePlayerAlias {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 玩家用户ID */
    private Long userId;

    /** 游戏类型：snake 贪吃蛇（预留扩展其他小游戏） */
    private String gameType;

    /** 玩家手动输入的匿名代号 */
    private String alias;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 由数据库 ON UPDATE CURRENT_TIMESTAMP 自动维护 */
    private LocalDateTime updatedAt;
}
