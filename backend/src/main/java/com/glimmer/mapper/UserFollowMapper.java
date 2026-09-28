package com.glimmer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.entity.UserFollow;
import org.apache.ibatis.annotations.Mapper;

/**
 * 交流会作者关注 Mapper
 */
@Mapper
public interface UserFollowMapper extends BaseMapper<UserFollow> {
}
