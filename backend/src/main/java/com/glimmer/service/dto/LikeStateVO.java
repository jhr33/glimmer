package com.glimmer.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文章点赞切换结果：当前是否已赞 + 文章最新点赞总数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LikeStateVO {

    /** 操作后是否处于已赞状态 */
    private Boolean liked;

    /** 文章最新点赞数 */
    private Integer likeCount;
}
