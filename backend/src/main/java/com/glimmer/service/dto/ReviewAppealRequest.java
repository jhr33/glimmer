package com.glimmer.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审核申诉请求
 */
@Data
public class ReviewAppealRequest {

    @NotBlank(message = "审核结果不能为空")
    private String result;

    @Size(max = 2000, message = "审核回复最长2000个字符")
    private String reply;

    /** 新的处罚类型（申诉成功时使用，null表示解除处罚）: warning/mute_24h/mute_7d/ban */
    private String newPenaltyType;
}
