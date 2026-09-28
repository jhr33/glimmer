package com.glimmer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.entity.GamePlayerAlias;
import org.apache.ibatis.annotations.Mapper;

/**
 * 小游戏玩家匿名代号 Mapper
 */
@Mapper
public interface GamePlayerAliasMapper extends BaseMapper<GamePlayerAlias> {
}
