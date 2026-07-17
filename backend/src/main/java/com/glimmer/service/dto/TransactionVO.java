package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 代币流水视图
 */
@Data
public class TransactionVO {

    private Long id;
    /** 类型: earn收入/spend支出 */
    private String type;
    private Integer amount;
    /** 来源: sign_in/receive_thanks/write_letter/create_campfire/ai_chat */
    private String source;
    private Long refId;
    private LocalDateTime createdAt;
}
