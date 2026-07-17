package com.glimmer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 花朵表（flower）
 */
@Data
@TableName("flower")
public class Flower {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long flowerTypeId;

    /** 当前阶段: seed种子/sprout幼苗/seedling中苗/bud花苞/bloom开放 */
    private String stage;

    private Integer stageWaterCount;

    private LocalDateTime plantedAt;

    private LocalDateTime lastWaterAt;

    private LocalDateTime bloomedAt;
}
