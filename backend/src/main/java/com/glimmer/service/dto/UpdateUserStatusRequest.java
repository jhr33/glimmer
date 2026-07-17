package com.glimmer.service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理员封禁/解封用户请求
 */
@Data
public class UpdateUserStatusRequest {

    @NotBlank(message = "状态不能为空")
    private String status;
}
