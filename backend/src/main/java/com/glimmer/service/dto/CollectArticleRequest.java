package com.glimmer.service.dto;

import lombok.Data;

/**
 * 收藏文章请求：指定收藏到哪个自定义文件夹
 */
@Data
public class CollectArticleRequest {

    /** 目标文件夹ID（为空时后端自动选用/创建"默认收藏"） */
    private Long folderId;
}
