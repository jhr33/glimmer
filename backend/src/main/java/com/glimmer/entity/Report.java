package com.glimmer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 举报记录表（report）
 */
@Data
@TableName("report")
public class Report {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long reporterId;

    private Long targetUserId;

    /** 目标类型: drift_bottle/bottle_reply/letter/campfire_message */
    private String targetType;

    private Long targetId;

    private String content;

    /** 状态: pending待审核/reviewed已审核 */
    private String status;

    /** 审核结果: approved举报成立/rejected举报驳回 */
    private String result;

    private Long reviewerId;

    private String reviewComment;

    private LocalDateTime reviewedAt;

    /** 处罚类型: null/空(无处罚)/warning(警告)/mute_24h(禁言24小时)/mute_7d(禁言7天)/ban(永久封禁) */
    private String penaltyType;

    /** 申诉次数 */
    private Integer appealCount;

    /** 最大申诉次数 */
    private static final Integer MAX_APPEAL_COUNT = 3;

    private LocalDateTime createdAt;
}
