package com.glimmer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.entity.Report;
import org.apache.ibatis.annotations.Mapper;

/**
 * 举报记录表 Mapper
 */
@Mapper
public interface ReportMapper extends BaseMapper<Report> {
}
