package com.glimmer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.entity.ArticleCollect;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 文章收藏 Mapper
 */
@Mapper
public interface ArticleCollectMapper extends BaseMapper<ArticleCollect> {

    /** 收藏数原子 +1 */
    @Update("UPDATE firefly_article SET favorite_count = favorite_count + 1 WHERE id = #{articleId}")
    int increaseFavoriteCount(@Param("articleId") Long articleId);

    /** 收藏数原子 -1（不小于 0） */
    @Update("UPDATE firefly_article SET favorite_count = GREATEST(favorite_count - 1, 0) WHERE id = #{articleId}")
    int decreaseFavoriteCount(@Param("articleId") Long articleId);

    /**
     * 批量查询当前用户在指定文章上的收藏关系（列表页回填收藏状态与所在文件夹）
     */
    @Select({
            "<script>",
            "SELECT * FROM article_collect WHERE user_id = #{userId}",
            "AND article_id IN",
            "<foreach collection='articleIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    List<ArticleCollect> selectByUserAndArticles(@Param("userId") Long userId,
                                                 @Param("articleIds") List<Long> articleIds);

    /** 统计文件夹内收藏数量 */
    @Select("SELECT COUNT(*) FROM article_collect WHERE folder_id = #{folderId}")
    long countByFolder(@Param("folderId") Long folderId);
}
