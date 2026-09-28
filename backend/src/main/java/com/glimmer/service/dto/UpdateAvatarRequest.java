package com.glimmer.service.dto;

import lombok.Data;

/**
 * 更新头像请求
 */
@Data
public class UpdateAvatarRequest {

    /** 头像URL；空字符串/null 表示恢复系统默认头像 */
    private String avatarUrl;
}
