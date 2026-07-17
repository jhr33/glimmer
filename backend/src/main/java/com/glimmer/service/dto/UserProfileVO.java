package com.glimmer.service.dto;

import lombok.Data;

/**
 * 他人主页公开信息
 */
@Data
public class UserProfileVO {

    private Long id;
    private String nickname;
    private String anonymousName;
    private Integer totalFirefly;
    /** 亮度等级 0-5 */
    private Integer brightnessLevel;
}
