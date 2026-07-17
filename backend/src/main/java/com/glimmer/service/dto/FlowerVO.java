package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 花朵视图
 */
@Data
public class FlowerVO {

    private Long id;
    private Long userId;
    private Long flowerTypeId;
    private String flowerTypeName;
    /** 当前阶段: seed/sprout/seedling/bud/bloom */
    private String stage;
    private Integer stageWaterCount;
    private LocalDateTime plantedAt;
    private LocalDateTime lastWaterAt;
    private LocalDateTime bloomedAt;
    /** 当前阶段浇水阈值（达到后进入下一阶段，bloom 阶段为 0） */
    private Integer currentStageThreshold;
    /** 进度百分比（0-100） */
    private Integer progressPercent;
}
