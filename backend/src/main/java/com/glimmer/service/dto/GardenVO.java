package com.glimmer.service.dto;

import lombok.Data;

import java.util.List;

/**
 * 萤火花园数据
 */
@Data
public class GardenVO {

    private Long userId;
    private Integer totalFirefly;
    private Integer fireflyBalance;
    /** 亮度等级 0-5（见开发文档 §2.7.2） */
    private Integer brightnessLevel;
    private List<FlowerVO> flowers;
}
