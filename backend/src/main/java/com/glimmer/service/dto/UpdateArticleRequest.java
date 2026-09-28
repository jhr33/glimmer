package com.glimmer.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 编辑萤火文章请求（我的随记）
 * 笔名、频道、正文、标签、公开状态均允许修改
 */
@Data
public class UpdateArticleRequest {

    @NotBlank(message = "笔名不能为空")
    @Size(max = 50, message = "笔名最长50个字符")
    private String penName;

    @NotBlank(message = "请选择频道")
    private String channel;

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题最长100个字符")
    private String title;

    @NotBlank(message = "正文不能为空")
    @Size(max = 50000, message = "正文最长50000个字符")
    private String content;

    @Size(max = 5, message = "最多选择5个标签")
    private List<String> tags;

    /** 图片URL列表（不限制张数，从正文 Markdown 标记提取回填；null 表示不修改） */
    private List<String> images;

    /** null 表示不修改；true/false 表示切换公开状态 */
    private Boolean isPublic;
}
