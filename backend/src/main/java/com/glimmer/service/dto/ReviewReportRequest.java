package com.glimmer.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审核举报请求
 */
@Data
public class ReviewReportRequest {

    @NotBlank(message = "审核结果不能为空")
    private String result;

    @Size(max = 500, message = "审核备注最长500个字符")
    private String reviewComment;

    /** 处罚类型: null/空(无处罚)/warning(警告)/mute_24h(禁言24小时)/mute_7d(禁言7天)/ban(永久封禁) */
    private String penaltyType;
}
