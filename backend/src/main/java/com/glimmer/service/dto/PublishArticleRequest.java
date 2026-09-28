package com.glimmer.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 发布萤火文章请求
 */
@Data
public class PublishArticleRequest {

    @NotBlank(message = "笔名不能为空")
    @Size(max = 50, message = "笔名最长50个字符")
    private String penName;

    /** 频道: chat 杂谈 / tech 技术笔记（Service 层做白名单校验） */
    @NotBlank(message = "请选择频道")
    private String channel;

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题最长100个字符")
    private String title;

    @NotBlank(message = "正文不能为空")
    @Size(max = 50000, message = "正文最长50000个字符")
    private String content;

    /** 自定义标签（最多5个，每个最长10个字符，Service 层清洗校验） */
    @Size(max = 5, message = "最多选择5个标签")
    private List<String> tags;

    /**
     * 图片URL列表（不限制张数）。
     * 图片以 Markdown 标记 ![](url) 内嵌在正文中；该字段由前端从正文提取回填，
     * 仅用于列表页缩略图预览等场景，不再做数量限制。
     */
    private List<String> images;

    /** 是否公开，不传默认公开 */
    private Boolean isPublic = Boolean.TRUE;
}
