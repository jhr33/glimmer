package com.glimmer.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发表文章评论请求
 */
@Data
public class CreateCommentRequest {

    /** 评论内容 */
    @NotBlank(message = "评论内容不能为空")
    @Size(max = 1000, message = "评论内容不能超过1000字")
    private String content;

    /** 被回复评论ID（一级评论不传） */
    private Long parentId;

    /** 对外身份模式: nickname 昵称 / anonymous 匿名（默认匿名） */
    private String displayMode;
}
