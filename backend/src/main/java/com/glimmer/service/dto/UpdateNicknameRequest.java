package com.glimmer.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改昵称请求
 */
@Data
public class UpdateNicknameRequest {

    @NotBlank(message = "昵称不能为空")
    @Size(min = 2, max = 20, message = "昵称长度为 2-20 个字符")
    private String nickname;
}
