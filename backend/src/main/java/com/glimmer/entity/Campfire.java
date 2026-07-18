package com.glimmer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 篝火表（campfire）
 */
@Data
@TableName("campfire")
public class Campfire {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 类型: default系统默认/custom用户创建 */
    private String type;

    private Integer maxMembers;

    private Long creatorId;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime lastActiveAt;
}
