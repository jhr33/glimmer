package com.glimmer.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 萤火交流会文章表（firefly_article）
 * <p>
 * pen_name（笔名）跟随文章存储，作为文章内容署名，
 * 不写入 user 账户字段——同一用户每篇文章都可以使用不同笔名。
 * </p>
 */
@Data
@TableName("firefly_article")
public class FireflyArticle {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 作者用户ID */
    private Long userId;

    /** 笔名（随文章内容署名，非账户字段） */
    private String penName;

    /** 频道: chat 杂谈 / tech 技术笔记 */
    private String channel;

    private String title;

    private String content;

    /** 标签，英文逗号分隔，如: Java,面试,随笔 */
    private String tags;

    /** 图片URL列表（JSON数组字符串，最多9张） */
    private String images;

    /** 是否公开: 1 公开（广场可见）/ 0 私密（仅自己可见） */
    private Integer isPublic;

    /** 浏览量 */
    private Integer viewCount;

    /** 点赞数（冗余计数，与 article_like 原子同步） */
    private Integer likeCount;

    /** 评论数（冗余计数） */
    private Integer commentCount;

    /** 收藏数（冗余计数） */
    private Integer favoriteCount;

    /**
     * 审核状态: APPROVED 审核通过（默认，广场可见）/
     * PENDING_REVIEW 待审核（被举报或打回后重新提交）/ RETURNED 屏蔽打回（仅作者可见）
     */
    private String reviewStatus;

    /** 打回/屏蔽原因（管理员审核时填写） */
    private String reviewReason;

    /** 审核状态常量：审核通过（默认） */
    public static final String REVIEW_APPROVED = "APPROVED";
    /** 审核状态常量：待审核 */
    public static final String REVIEW_PENDING = "PENDING_REVIEW";
    /** 审核状态常量：屏蔽打回 */
    public static final String REVIEW_RETURNED = "RETURNED";

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
