package com.glimmer.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 关注作者请求：关注目标为「账户 + 笔名」组合
 */
@Data
public class CreateFollowRequest {

    /** 被关注账户用户ID（文章作者） */
    @NotNull(message = "作者不能为空")
    private Long followeeId;

    /** 关注的作者笔名 */
    @NotBlank(message = "笔名不能为空")
    @Size(max = 50, message = "笔名过长")
    private String penName;

    /** 自定义备注（可选，区分不同账户同一笔名） */
    @Size(max = 50, message = "备注最长50字")
    private String remark;
}
