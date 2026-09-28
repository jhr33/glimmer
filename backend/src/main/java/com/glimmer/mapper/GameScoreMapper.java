package com.glimmer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.entity.GameScore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 小游戏成绩 Mapper
 */
@Mapper
public interface GameScoreMapper extends BaseMapper<GameScore> {

    /**
     * 排行榜：每个用户只取最高分一局（同分时取最新产生的一条）。
     * 思路：自连接 LEFT JOIN，排除"同一用户还存在更优记录"的行，
     * 兼容 MySQL 5.7（不使用窗口函数 ROW_NUMBER）。
     * 排序：分数降序，同分按最早达到时间升序（先创纪录者排前）。
     *
     * @param gameType 游戏类型
     * @param limit    返回条数
     */
    // 手写 SQL 的结果映射不走 MP 的 @TableField，下划线转驼峰也无法把
    // is_anonymous 映射到属性 anonymous，这里显式声明避免匿名字段丢失
    @Results(@Result(column = "is_anonymous", property = "anonymous"))
    @Select("SELECT gs.* FROM game_score gs " +
            "LEFT JOIN game_score gs2 " +
            "  ON gs2.user_id = gs.user_id " +
            " AND gs2.game_type = gs.game_type " +
            " AND (gs2.score > gs.score OR (gs2.score = gs.score AND gs2.id > gs.id)) " +
            "WHERE gs.game_type = #{gameType} AND gs2.id IS NULL " +
            "ORDER BY gs.score DESC, gs.created_at ASC " +
            "LIMIT #{limit}")
    List<GameScore> selectLeaderboard(@Param("gameType") String gameType,
                                      @Param("limit") int limit);

    /**
     * 查询某分数在排行榜中的名次：
     * 历史最高分比该分数更高的去重用户数 + 1（兼容 MySQL 5.7 子查询写法）。
     *
     * @param score 待比较的分数
     * @return 名次（从 1 开始）
     */
    @Select("SELECT COUNT(*) + 1 FROM (" +
            "  SELECT user_id FROM game_score " +
            "  WHERE game_type = #{gameType} " +
            "  GROUP BY user_id " +
            "  HAVING MAX(score) > #{score}" +
            ") t")
    Integer selectRankByScore(@Param("gameType") String gameType,
                              @Param("score") int score);

    /**
     * 统计用户在某游戏上的总局数
     */
    @Select("SELECT COUNT(*) FROM game_score " +
            "WHERE game_type = #{gameType} AND user_id = #{userId}")
    Integer countByUserAndGame(@Param("gameType") String gameType,
                               @Param("userId") Long userId);
}
