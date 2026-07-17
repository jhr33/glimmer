package com.glimmer.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 提交意见信请求
 */
@Data
public class CreateFeedbackRequest {

    @NotBlank(message = "内容不能为空")
    @Size(max = 2000, message = "内容最长2000个字符")
    private String content;
}
