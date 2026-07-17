package com.glimmer.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 回复信件请求
 */
@Data
public class ReplyLetterRequest {

    @NotBlank(message = "内容不能为空")
    @Size(max = 2000, message = "内容最长2000个字符")
    private String content;
}
