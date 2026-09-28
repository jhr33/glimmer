package com.glimmer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.entity.CommentLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 评论点赞 Mapper
 */
@Mapper
public interface CommentLikeMapper extends BaseMapper<CommentLike> {

    /** 评论点赞数原子 +1 */
    @Update("UPDATE article_comment SET like_count = like_count + 1 WHERE id = #{commentId}")
    int increaseCommentLikeCount(@Param("commentId") Long commentId);

    /** 评论点赞数原子 -1（不小于 0） */
    @Update("UPDATE article_comment SET like_count = GREATEST(like_count - 1, 0) WHERE id = #{commentId}")
    int decreaseCommentLikeCount(@Param("commentId") Long commentId);

    /**
     * 批量查询当前用户已点赞的评论ID（评论列表状态回填，避免 N+1）
     */
    @Select({
            "<script>",
            "SELECT comment_id FROM comment_like WHERE user_id = #{userId}",
            "AND comment_id IN",
            "<foreach collection='commentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    List<Long> selectLikedCommentIds(@Param("userId") Long userId,
                                     @Param("commentIds") List<Long> commentIds);
}
