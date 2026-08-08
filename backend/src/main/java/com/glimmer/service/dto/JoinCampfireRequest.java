package com.glimmer.service.dto;

import lombok.Data;

/**
 * 加入篝火请求
 */
@Data
public class JoinCampfireRequest {

    /**
     * 显示模式："nickname"(使用用户昵称) 或 "anonymous"(随机匿名名称)
     * 默认为 "nickname"
     */
    private String displayMode;
}
