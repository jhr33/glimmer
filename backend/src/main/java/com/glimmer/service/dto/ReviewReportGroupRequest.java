package com.glimmer.service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 审核聚合举报请求
 */
@Data
public class ReviewReportGroupRequest {

    /** 目标类型: drift_bottle/bottle_reply/letter/campfire_message */
    @NotBlank(message = "目标类型不能为空")
    private String targetType;

    /** 目标资源ID */
    private Long targetId;

    /** 审核结果: approved/rejected */
    @NotBlank(message = "审核结果不能为空")
    private String result;

    /** 处罚类型: null/warning/mute_24h/mute_7d/ban */
    private String penaltyType;

    /** 审核备注 */
    private String reviewComment;
}
