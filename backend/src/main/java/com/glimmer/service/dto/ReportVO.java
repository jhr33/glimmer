package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 举报视图
 */
@Data
public class ReportVO {

    private Long id;
    private Long reporterId;
    private String reporterUsername;
    private Long targetUserId;
    private String targetUsername;
    private String targetType;
    private Long targetId;
    private String content;
    private String status;
    private String result;
    private Long reviewerId;
    private String reviewComment;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    /** 关联处罚单ID */
    private Long punishmentId;
    /** 申诉次数 */
    private Integer appealCount;
    /** 被举报内容 */
    private String reportedContent;
    /** 发言场所 */
    private String location;
}
