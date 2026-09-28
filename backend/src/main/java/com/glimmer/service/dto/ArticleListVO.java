package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 萤火文章列表项 VO（广场列表 / 我的随记共用）
 */
@Data
public class ArticleListVO {

    private Long id;

    /** 作者用户ID（用于无自定义头像时按 userId 选取系统默认头像） */
    private Long authorId;

    private String penName;

    /** 作者头像URL（null = 系统默认头像，前端按 userId 取默认头像） */
    private String authorAvatar;

    private String channel;

    private String title;

    /** 正文摘要（后端截取前120字） */
    private String summary;

    /** 正文全文（仅管理员审核列表返回，用于内容审核） */
    private String content;

    private List<String> tags;

    /** 图片URL列表（后端由 JSON 字符串解析，列表页只取前3张） */
    private List<String> images;

    private Boolean isPublic;

    private Integer viewCount;

    /** 点赞数 */
    private Integer likeCount;

    /** 评论数 */
    private Integer commentCount;

    /** 收藏数 */
    private Integer favoriteCount;

    /** 当前访问者是否已点赞（游客为 false，后端按登录态批量回填） */
    private Boolean liked;

    /** 当前访问者是否已收藏 */
    private Boolean collected;

    /** 所在收藏文件夹ID（我的收藏列表使用） */
    private Long folderId;

    /** 收藏时间（我的收藏列表使用） */
    private LocalDateTime collectedAt;

    /** 审核状态（作者本人视角可见）: APPROVED/PENDING_REVIEW/RETURNED */
    private String reviewStatus;

    /** 打回/屏蔽原因（管理员审核时填写） */
    private String reviewReason;

    private LocalDateTime createdAt;
}
