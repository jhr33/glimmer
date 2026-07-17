package com.glimmer.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建篝火请求
 */
@Data
public class CreateCampfireRequest {

    @NotBlank(message = "篝火名称不能为空")
    @Size(max = 50, message = "篝火名称最长50个字符")
    private String name;

    private Integer maxMembers;
}
