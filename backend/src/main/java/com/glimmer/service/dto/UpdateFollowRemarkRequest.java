package com.glimmer.service.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改关注备注请求
 */
@Data
public class UpdateFollowRemarkRequest {

    /** 自定义备注（可传空串清除备注） */
    @Size(max = 50, message = "备注最长50字")
    private String remark;
}
