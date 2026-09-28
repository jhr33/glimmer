package com.glimmer.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 交流会文章评论表（article_comment）
 * <p>
 * display_name 为评论瞬间的展示名快照（昵称或匿名昵称），
 * 历史评论不随用户后续改名/匿名轮换而变化。
 * </p>
 */
@Data
@TableName("article_comment")
public class ArticleComment {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文章ID */
    private Long articleId;

    /** 评论用户ID */
    private Long userId;

    /** 被回复评论ID（一级评论为 null） */
    private Long parentId;

    /** 被回复者展示名快照 */
    private String replyToName;

    /** 评论时展示名（昵称或匿名昵称快照） */
    private String displayName;

    /** 是否匿名: 1 是 / 0 否 */
    private Integer isAnonymous;

    /** 评论内容 */
    private String content;

    /** 状态: ACTIVE 正常 / DELETED 已删除 */
    private String status;

    /** 评论点赞数（冗余计数，与 comment_like 原子同步） */
    private Integer likeCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DELETED = "DELETED";
}
