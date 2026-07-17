package com.glimmer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.entity.CampfireMember;
import org.apache.ibatis.annotations.Mapper;

/**
 * 篝火成员表 Mapper（新增表）
 */
@Mapper
public interface CampfireMemberMapper extends BaseMapper<CampfireMember> {
}
