package com.glimmer.service.dto;

import lombok.Data;

/**
 * 签到响应
 */
@Data
public class SignInResponse {

    /** 是否签到成功 */
    private Boolean signedIn;
    /** 本次获得代币数 */
    private Integer reward;
    /** 累计签到天数 */
    private Integer totalSignDays;
}
