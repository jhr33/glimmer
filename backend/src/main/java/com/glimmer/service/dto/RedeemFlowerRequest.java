package com.glimmer.service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 兑换花种请求
 */
@Data
public class RedeemFlowerRequest {

    @NotNull(message = "花种ID不能为空")
    private Long flowerTypeId;
}
