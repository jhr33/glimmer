package com.glimmer.service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理员审核文章请求
 */
@Data
public class ReviewArticleRequest {

    /** 审核动作: approve 通过 / return 屏蔽打回 */
    @NotBlank(message = "审核动作不能为空")
    private String action;

    /** 打回原因（action=return 时填写，通知作者） */
    private String reason;
}
