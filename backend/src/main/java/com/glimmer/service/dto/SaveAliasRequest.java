package com.glimmer.service.dto;

import lombok.Data;

/**
 * 保存（设置/修改）游戏匿名代号请求
 */
@Data
public class SaveAliasRequest {

    /** 游戏类型（当前仅支持 snake） */
    private String gameType;

    /** 玩家手动输入的匿名代号 */
    private String alias;
}
