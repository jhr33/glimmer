package com.glimmer.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 回复意见信请求
 */
@Data
public class ReplyFeedbackRequest {

    @NotBlank(message = "回复内容不能为空")
    @Size(max = 2000, message = "回复内容最长2000个字符")
    private String reply;
}
