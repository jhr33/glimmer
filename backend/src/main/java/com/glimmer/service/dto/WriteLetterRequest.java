package com.glimmer.service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 写信请求
 */
@Data
public class WriteLetterRequest {

    @NotNull(message = "收信人ID不能为空")
    private Long receiverId;

    @NotBlank(message = "内容不能为空")
    @Size(max = 2000, message = "内容最长2000个字符")
    private String content;

    @NotNull(message = "来源回复ID不能为空")
    private Long sourceBottleReplyId;
}
