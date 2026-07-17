package com.glimmer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 花种配置表（flower_type）
 */
@Data
@TableName("flower_type")
public class FlowerType {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private Integer seedToSprout;

    private Integer sproutToSeedling;

    private Integer seedlingToBud;

    private Integer budToBloom;

    private String iconSeed;

    private String iconSprout;

    private String iconSeedling;

    private String iconBud;

    private String iconBloom;

    private Integer redeemFirefly;

    private Integer requiredFirefly;

    /** 是否上架: 0否/1是 */
    private Integer available;

    private LocalDateTime createdAt;
}
