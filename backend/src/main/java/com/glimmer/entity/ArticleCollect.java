package com.glimmer.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文章收藏关系表（article_collect）
 * 唯一索引 uk_article_user(article_id, user_id)：一篇文章对同一用户只收藏一次
 */
@Data
@TableName("article_collect")
public class ArticleCollect {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long articleId;

    private Long userId;

    /** 所在收藏文件夹ID */
    private Long folderId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
