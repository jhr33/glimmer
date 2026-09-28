package com.glimmer.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评论点赞切换结果：当前是否已赞 + 评论最新点赞总数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentLikeStateVO {

    /** 操作后是否处于已赞状态 */
    private Boolean liked;

    /** 评论最新点赞数 */
    private Integer likeCount;
}
