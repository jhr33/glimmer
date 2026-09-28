package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章评论 VO
 */
@Data
public class ArticleCommentVO {

    private Long id;

    private Long articleId;

    private Long userId;

    /** 被回复评论ID（一级评论为 null） */
    private Long parentId;

    /** 被回复者展示名快照 */
    private String replyToName;

    /** 评论时展示名（昵称或匿名昵称快照，历史评论不随后续改名变化） */
    private String displayName;

    /** 评论者头像（匿名评论为 null，前端显示系统默认头像） */
    private String avatarUrl;

    /** 是否匿名发布 */
    private Boolean isAnonymous;

    private String content;

    /** 评论点赞数 */
    private Integer likeCount;

    /** 当前访问者是否已赞该评论（游客为 false） */
    private Boolean liked;

    private LocalDateTime createdAt;

    /** 当前访问者是否为评论作者（前端据此显示删除按钮） */
    private Boolean owner;

    /** 该评论是否由文章作者本人发布（前端显示"作者"标记，且 displayName 固定为文章 penName） */
    private Boolean isAuthor;
}
