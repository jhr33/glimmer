package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 花种视图
 */
@Data
public class FlowerTypeVO {

    private Long id;
    private String name;
    private String description;
    private Integer redeemFirefly;
    private Integer requiredFirefly;
    private Integer available;
    private String iconSeed;
    private String iconSprout;
    private String iconSeedling;
    private String iconBud;
    private String iconBloom;
    private LocalDateTime createdAt;
}
