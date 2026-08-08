package com.glimmer.service.dto;

import lombok.Data;

/**
 * AI 对话额度解锁响应
 * <p>
 * 用户消耗 1 枚代币解锁额外轮次后返回，前端据此更新额度显示与代币余额。
 */
@Data
public class UnlockQuotaResponse {

    /** 会话ID */
    private Long conversationId;

    /** 解锁后已用轮次 */
    private Integer quotaUsed;

    /** 解锁后轮次上限 */
    private Integer quotaLimit;

    /** 用户最新代币余额 */
    private Integer tokenBalance;

    /** 是否成功生成摘要（best-effort，失败仅记录日志） */
    private Boolean summaryGenerated;
}
