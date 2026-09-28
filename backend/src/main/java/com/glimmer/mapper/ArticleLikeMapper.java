package com.glimmer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.entity.ArticleLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 文章点赞 Mapper
 */
@Mapper
public interface ArticleLikeMapper extends BaseMapper<ArticleLike> {

    /** 点赞数原子 +1 */
    @Update("UPDATE firefly_article SET like_count = like_count + 1 WHERE id = #{articleId}")
    int increaseLikeCount(@Param("articleId") Long articleId);

    /** 点赞数原子 -1（不小于 0） */
    @Update("UPDATE firefly_article SET like_count = GREATEST(like_count - 1, 0) WHERE id = #{articleId}")
    int decreaseLikeCount(@Param("articleId") Long articleId);

    /**
     * 批量查询当前用户已点赞的文章ID（列表页状态回填，避免 N+1）
     */
    @Select({
            "<script>",
            "SELECT article_id FROM article_like WHERE user_id = #{userId}",
            "AND article_id IN",
            "<foreach collection='articleIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    List<Long> selectLikedArticleIds(@Param("userId") Long userId,
                                     @Param("articleIds") List<Long> articleIds);
}
