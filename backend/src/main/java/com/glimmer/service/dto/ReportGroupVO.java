package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聚合举报视图（按目标资源分组）
 * 同一条信息被多次举报时，显示为一条代办
 */
@Data
public class ReportGroupVO {

    /** 目标类型: drift_bottle/bottle_reply/letter/campfire_message */
    private String targetType;

    /** 目标资源ID */
    private Long targetId;

    /** 被举报人ID */
    private Long targetUserId;

    /** 被举报人昵称 */
    private String targetUsername;

    /** 被举报内容 */
    private String reportedContent;

    /** 发言场所 */
    private String location;

    /** 举报人数 */
    private Integer reporterCount;

    /** 最早举报时间 */
    private LocalDateTime firstReportedAt;

    /** 最近举报时间 */
    private LocalDateTime lastReportedAt;

    /** 状态: pending(有待审核)/reviewed(已全部审核) */
    private String groupStatus;

    /** 审核结果（若已全部审核）: approved/rejected */
    private String groupResult;

    /** 关联的所有举报记录 */
    private List<ReportVO> reports;
}
