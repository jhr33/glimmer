package com.glimmer.service;

import com.glimmer.common.response.PageResult;
import com.glimmer.service.dto.GardenVO;
import com.glimmer.service.dto.UpdateNicknameRequest;
import com.glimmer.service.dto.UserAdminVO;
import com.glimmer.service.dto.UserProfileVO;
import com.glimmer.service.dto.UserVO;

/**
 * 用户服务
 */
public interface UserService {

    /**
     * 获取当前登录用户信息
     */
    UserVO getCurrentUserInfo(Long userId);

    /**
     * 修改昵称
     */
    void updateNickname(Long userId, UpdateNicknameRequest request);

    /**
     * 查看他人主页（仅公开信息）
     */
    UserProfileVO getUserProfile(Long userId);

    /**
     * 获取花园数据（萤火值、亮度等级、花朵列表）
     */
    GardenVO getUserGarden(Long userId);

    /**
     * 管理员用户列表，可按 status / role 筛选
     */
    PageResult<UserAdminVO> getUserListForAdmin(String status, String role, int page, int size);

    /**
     * 管理员封禁/解封用户
     *
     * @param adminId 操作管理员ID
     * @param userId  目标用户ID
     * @param status  目标状态: active/banned
     */
    void updateUserStatus(Long adminId, Long userId, String status);

    /**
     * 检查用户是否被禁言或封禁（用于发布内容前校验）
     * 若被封禁抛出 USER_BANNED，若被禁言抛出 USER_MUTED
     */
    void checkUserNotMuted(Long userId);

    /**
     * 获取用户的 AI 记忆关键信息（user.ai_context 字段，JSON 字符串原值）
     * 供 AI 服务在系统提示词中注入用户记忆。
     *
     * @return ai_context 原文，可能为 null
     */
    String getAiContext(Long userId);
}
