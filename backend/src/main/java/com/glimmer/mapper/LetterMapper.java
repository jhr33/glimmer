package com.glimmer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.entity.Letter;
import org.apache.ibatis.annotations.Mapper;

/**
 * 信件表 Mapper
 */
@Mapper
public interface LetterMapper extends BaseMapper<Letter> {
}
