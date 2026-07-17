package com.glimmer.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 代币流水表（token_transaction）
 */
@Data
@TableName("token_transaction")
public class TokenTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 类型: earn收入/spend支出 */
    private String type;

    private Integer amount;

    /** 来源: sign_in/receive_thanks/write_letter/create_campfire/ai_chat */
    private String source;

    private Long refId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
