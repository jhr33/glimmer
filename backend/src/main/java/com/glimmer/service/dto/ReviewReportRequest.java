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
}
