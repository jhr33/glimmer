package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 排行榜条目 VO（每个用户只展示其最高分一局）
 */
@Data
public class LeaderboardItemVO {

    /** 排行榜名次（从 1 开始） */
    private Integer rank;

    private Long userId;

    /** 最高分那局的昵称快照（匿名时为匿名昵称） */
    private String displayName;

    /** 该条目是否匿名上榜（前端据此展示匿名标识，不暴露真实身份） */
    private Boolean anonymous;

    /** 历史最高分 */
    private Integer score;

    /** 最高分那局达到的难度等级 */
    private Integer level;

    /** 最高分那局的时长（秒） */
    private Integer durationSeconds;

    /** 最高分产生时间 */
    private LocalDateTime createdAt;

    /** 是否为当前登录用户（前端高亮当前玩家行） */
    private Boolean currentUser;
}
