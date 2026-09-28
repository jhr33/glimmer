package com.glimmer.service.dto;

import lombok.Data;

/**
 * 提交游戏成绩请求
 */
@Data
public class SubmitScoreRequest {

    /** 游戏类型（当前仅支持 snake） */
    private String gameType;

    /** 本局得分 */
    private Integer score;

    /** 本局达到的难度等级 */
    private Integer level;

    /** 本局时长（秒） */
    private Integer durationSeconds;

    /** 排行榜是否匿名展示：true 时以玩家平台匿名昵称上榜 */
    private Boolean anonymous;
}
