package com.glimmer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.glimmer.entity.TokenTransaction;
import org.apache.ibatis.annotations.Mapper;

/**
 * 代币流水表 Mapper
 */
@Mapper
public interface TokenTransactionMapper extends BaseMapper<TokenTransaction> {
}
