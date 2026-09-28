package com.glimmer.service.dto;

import lombok.Data;

/**
 * 我的游戏匿名代号 VO
 */
@Data
public class GameAliasVO {

    /** 游戏类型 */
    private String gameType;

    /**
     * 已保存的匿名代号；未设置时为 null（前端展示输入框占位）
     */
    private String alias;
}
