package com.glimmer.service;

import com.glimmer.common.response.PageResult;
import com.glimmer.service.dto.SignInResponse;
import com.glimmer.service.dto.SignInStatusResponse;
import com.glimmer.service.dto.TransactionVO;

/**
 * 代币服务（签到、流水查询）
 */
public interface TokenService {

    /**
     * 签到（每日1次）
     *
     * @return 签到结果（含获得代币数）
     */
    SignInResponse signIn(Long userId);

    /**
     * 查询今日签到状态
     */
    SignInStatusResponse getSignInStatus(Long userId);

    /**
     * 代币流水查询（分页，可按类型/来源筛选）
     *
     * @param userId 用户ID
     * @param type   类型：earn/spend（可空）
     * @param source 来源（可空）
     * @param page   页码（从1开始）
     * @param size   每页条数
     */
    PageResult<TransactionVO> getTransactions(Long userId, String type, String source, int page, int size);
}
