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
}
