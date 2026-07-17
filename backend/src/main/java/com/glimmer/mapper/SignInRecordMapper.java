package com.glimmer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.entity.SignInRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 签到记录表 Mapper
 */
@Mapper
public interface SignInRecordMapper extends BaseMapper<SignInRecord> {
}
