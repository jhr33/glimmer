package com.glimmer.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建收藏文件夹请求（文件夹名由用户自定义）
 */
@Data
public class CreateFolderRequest {

    @NotBlank(message = "文件夹名称不能为空")
    @Size(max = 30, message = "文件夹名称不能超过30字")
    private String name;
}
