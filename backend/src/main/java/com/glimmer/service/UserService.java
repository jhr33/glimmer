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
}
