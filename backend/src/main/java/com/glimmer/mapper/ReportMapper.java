package com.glimmer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.entity.Report;
import org.apache.ibatis.annotations.Mapper;

/**
 * 举报记录表 Mapper
 */
@Mapper
public interface ReportMapper extends BaseMapper<Report> {

    /**
     * 获取被举报成立的目标ID列表
     * @param targetType 目标类型
     * @return 被举报成立的目标ID列表
     */
    @org.apache.ibatis.annotations.Select("SELECT DISTINCT target_id FROM report WHERE target_type = #{targetType} AND status = 'reviewed' AND result = 'approved'")
    java.util.List<Long> selectApprovedTargetIds(java.lang.String targetType);
}
