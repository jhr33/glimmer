package com.glimmer.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 交流会作者关注表（user_follow）
 * <p>
    * 笔名跟随文章存储而非账户字段，因此关注目标为「账户 + 笔名」组合：
 * 同一账户的多篇文章可能使用不同笔名，可分别关注；
 * 自定义备注 remark 用于区分不同账户使用同一笔名的情况。
 * </p>
 */
@Data
@TableName("user_follow")
public class UserFollow {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关注者用户ID */
    private Long followerId;

    /** 被关注账户用户ID */
    private Long followeeId;

    /** 关注的作者笔名 */
    private String penName;

    /** 自定义备注（区分不同账户同一笔名） */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
