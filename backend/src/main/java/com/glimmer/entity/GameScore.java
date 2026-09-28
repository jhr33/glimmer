package com.glimmer.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 小游戏成绩表（game_score）
 * 每局游戏结束写入一条；排行榜按用户聚合取最高分
 */
@Data
@TableName("game_score")
public class GameScore {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 玩家用户ID */
    private Long userId;

    /** 游戏类型：snake 贪吃蛇（预留扩展其他小游戏） */
    private String gameType;

    /** 成绩产生时的昵称快照（排行榜展示用，避免改名影响历史成绩） */
    private String displayName;

    /** 排行榜是否匿名展示（玩家每局提交时自选） */
    @TableField("is_anonymous")
    private Boolean anonymous;

    /** 匿名昵称快照（匿名时从平台统一匿名昵称复制，仅排行榜展示用） */
    private String anonymousName;

    /** 本局得分 */
    private Integer score;

    /** 本局达到的难度等级 */
    private Integer level;

    /** 本局时长（秒） */
    private Integer durationSeconds;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
