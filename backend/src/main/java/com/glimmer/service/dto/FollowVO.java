package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作者关注记录 VO（我的关注列表使用）
 */
@Data
public class FollowVO {

    /** 关注记录ID */
    private Long id;

    /** 被关注账户用户ID */
    private Long followeeId;

    /** 关注的作者笔名 */
    private String penName;

    /** 自定义备注（区分不同账户同一笔名） */
    private String remark;

    /** 该账户该笔名下当前可见的文章数（公开且审核通过） */
    private Long articleCount;

    private LocalDateTime createdAt;
}
