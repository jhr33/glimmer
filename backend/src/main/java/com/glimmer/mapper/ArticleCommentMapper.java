package com.glimmer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.entity.ArticleComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 文章评论 Mapper
 */
@Mapper
public interface ArticleCommentMapper extends BaseMapper<ArticleComment> {

    /** 评论数原子 +1（新增评论） */
    @Update("UPDATE firefly_article SET comment_count = comment_count + 1 WHERE id = #{articleId}")
    int increaseCommentCount(@Param("articleId") Long articleId);

    /** 评论数原子 -1（删除评论，不小于 0） */
    @Update("UPDATE firefly_article SET comment_count = GREATEST(comment_count - 1, 0) WHERE id = #{articleId}")
    int decreaseCommentCount(@Param("articleId") Long articleId);

    /** 统计文章有效评论数（用于校准冗余计数） */
    @Select("SELECT COUNT(*) FROM article_comment WHERE article_id = #{articleId} AND status = 'ACTIVE'")
    long countActiveByArticle(@Param("articleId") Long articleId);
}
