package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 萤火文章详情 VO
 */
@Data
public class ArticleVO {

    private Long id;

    private Long userId;

    /** 文章署名笔名 */
    private String penName;

    /** 作者头像URL（null = 系统默认头像，前端按 userId 取默认头像） */
    private String authorAvatar;

    /** 频道: chat 杂谈 / tech 技术笔记 */
    private String channel;

    private String title;

    private String content;

    /** 标签列表（后端由逗号分隔字符串拆出） */
    private List<String> tags;

    /** 图片URL列表（后端由 JSON 字符串解析） */
    private List<String> images;

    private Boolean isPublic;

    private Integer viewCount;

    /** 点赞数 */
    private Integer likeCount;

    /** 评论数 */
    private Integer commentCount;

    /** 收藏数 */
    private Integer favoriteCount;

    /** 当前访问者是否已点赞（游客为 false） */
    private Boolean liked;

    /** 当前访问者是否已收藏（游客为 false） */
    private Boolean collected;

    /** 当前访问者收藏该文章所在的文件夹ID（未收藏为 null） */
    private Long folderId;

    /** 当前访问者是否已关注该文章的「账户+笔名」作者（游客为 false） */
    private Boolean following;

    /** 当前访问者对该作者的关注记录ID（未关注为 null，用于取消关注） */
    private Long followId;

    /** 审核状态（作者本人视角可见）: APPROVED/PENDING_REVIEW/RETURNED */
    private String reviewStatus;

    /** 打回/屏蔽原因（管理员审核时填写） */
    private String reviewReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 当前访问者是否为作者（前端据此显示编辑/删除入口） */
    private Boolean owner;
}
