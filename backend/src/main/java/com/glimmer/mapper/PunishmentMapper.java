package com.glimmer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.entity.Punishment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 处罚单Mapper
 */
@Mapper
public interface PunishmentMapper extends BaseMapper<Punishment> {

    /**
     * 查询用户当前生效的处罚列表（排除已过期的记录）
     * @param userId 用户ID
     * @return 生效的处罚列表
     */
    @Select("SELECT * FROM punishment WHERE user_id = #{userId} AND status = 'ACTIVE' AND (end_at IS NULL OR end_at > NOW()) ORDER BY created_at DESC")
    List<Punishment> selectActiveByUserId(@Param("userId") Long userId);

    /**
     * 查询用户当前生效的处罚（最新一条，排除已过期的记录）
     * @param userId 用户ID
     * @return 最新生效的处罚
     */
    @Select("SELECT * FROM punishment WHERE user_id = #{userId} AND status = 'ACTIVE' AND (end_at IS NULL OR end_at > NOW()) ORDER BY created_at DESC LIMIT 1")
    Punishment selectLatestActiveByUserId(@Param("userId") Long userId);

    /**
     * 查询需要过期的处罚记录（end_at < NOW() 且 status = ACTIVE）
     * @param now 当前时间
     * @return 需要过期的处罚列表
     */
    @Select("SELECT * FROM punishment WHERE status = 'ACTIVE' AND end_at IS NOT NULL AND end_at < #{now}")
    List<Punishment> selectExpired(@Param("now") LocalDateTime now);

    /**
     * 根据来源ID和来源类型查询处罚记录
     * @param sourceId 来源ID
     * @param sourceType 来源类型
     * @return 处罚记录
     */
    @Select("SELECT * FROM punishment WHERE source_id = #{sourceId} AND source_type = #{sourceType}")
    List<Punishment> selectBySource(@Param("sourceId") Long sourceId, @Param("sourceType") String sourceType);
}