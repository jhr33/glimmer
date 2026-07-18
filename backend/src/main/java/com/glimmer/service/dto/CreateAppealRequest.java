package com.glimmer.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 提交申诉请求
 */
@Data
public class CreateAppealRequest {

    /** 举报ID（从处罚通知进入时自动填写，也可手动填写） */
    private Long reportId;

    @NotBlank(message = "申诉内容不能为空")
    @Size(max = 2000, message = "申诉内容最长2000个字符")
    private String content;
}