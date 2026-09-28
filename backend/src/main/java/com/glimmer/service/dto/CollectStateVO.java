package com.glimmer.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 收藏操作结果：当前是否已收藏 + 所在文件夹 + 文章最新收藏总数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CollectStateVO {

    /** 操作后是否处于已收藏状态（取消收藏时为 false） */
    private Boolean collected;

    /** 所在文件夹ID（取消收藏时为 null） */
    private Long folderId;

    /** 文章最新收藏数 */
    private Integer favoriteCount;
}
