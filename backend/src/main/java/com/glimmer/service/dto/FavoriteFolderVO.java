package com.glimmer.service.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收藏文件夹 VO（我的收藏页按文件夹分组展示）
 */
@Data
public class FavoriteFolderVO {

    private Long id;

    /** 用户自定义文件夹名 */
    private String name;

    /** 文件夹内收藏文章数 */
    private Integer collectCount;

    private LocalDateTime createdAt;
}
