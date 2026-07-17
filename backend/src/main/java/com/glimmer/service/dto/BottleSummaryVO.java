package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 漂流瓶列表摘要视图（游客可看，不含内容）
 */
@Data
public class BottleSummaryVO {

    private Long id;
    private LocalDateTime createdAt;
}
