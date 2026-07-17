package com.glimmer.service.dto;

import lombok.Data;

/**
 * 签到状态查询响应
 */
@Data
public class SignInStatusResponse {

    /** 今日是否已签到 */
    private Boolean signedInToday;
    /** 累计签到天数 */
    private Integer totalSignDays;
}
