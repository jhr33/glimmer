package com.glimmer.service.dto;

import lombok.Data;

/**
 * 成绩提交结果 VO
 */
@Data
public class SubmitScoreResultVO {

    /** 入库的成绩ID */
    private Long id;

    /** 本局得分 */
    private Integer score;

    /** 本局达到的难度等级 */
    private Integer level;

    /** 本局时长（秒） */
    private Integer durationSeconds;

    /** 是否刷新了个人历史最高分 */
    private Boolean newPersonalBest;

    /** 提交后的历史最佳名次（从 1 开始） */
    private Integer rank;

    /** 本局是否匿名上榜 */
    private Boolean anonymous;

    /** 本局在排行榜上实际展示的名称（实名=昵称，匿名=匿名昵称），供前端即时反馈 */
    private String displayName;
}
