package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 捡到漂流瓶后的视图（不含内容）
 */
@Data
public class BottlePickVO {

    private Long bottleId;
    private LocalDateTime createdAt;
}
