package com.glimmer.service;

import com.glimmer.entity.User;

/**
 * 回音机器人服务
 * 见开发文档 §9 AI 机器人系统
 */
public interface EchoService {

    /**
     * 回音机器人用户名
     */
    String BOT_USERNAME = "bot_echo";

    /**
     * 回音机器人角色
     */
    String ROLE_BOT = "bot";

    /**
     * 获取（或懒加载）回音机器人账号
     */
    User getOrCreateBotUser();

    /**
     * 托管开关：判断是否开启 AI 自动托管
     */
    boolean isAutoModeEnabled();

    /**
     * 设置托管开关
     */
    void setAutoMode(boolean enabled);

    /**
     * 获取托管开关状态
     */
    boolean getAutoMode();

    /**
     * 处理漂流瓶自动回复（扫描并回复未回复的漂流瓶）
     *
     * @param limit 本次最多处理的瓶子数量
     * @return 实际回复的瓶子数量
     */
    int processPendingBottles(int limit);

    /**
     * 处理篝火自动救场（冷场超过阈值时 AI 发话题）
     *
     * @param coldSeconds 冷场判定秒数
     * @return 是否救场发言
     */
    boolean processCampfireColdspot(int coldSeconds);
}
