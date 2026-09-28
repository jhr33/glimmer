package com.glimmer.service;

import com.glimmer.common.response.PageResult;
import com.glimmer.entity.User;
import com.glimmer.service.dto.ChangePasswordRequest;
import com.glimmer.service.dto.GardenVO;
import com.glimmer.service.dto.UpdateNicknameRequest;
import com.glimmer.service.dto.UserAdminVO;
import com.glimmer.service.dto.UserProfileVO;
import com.glimmer.service.dto.UserVO;

import java.util.Collection;
import java.util.Map;

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
     * 更新头像（传空值表示清除自定义头像，恢复系统默认头像）
     */
    void updateAvatar(Long userId, String avatarUrl);

    /**
     * 修改密码（一天限一次）
     */
    void changePassword(Long userId, ChangePasswordRequest request);

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

    /**
     * 获取用户统一匿名昵称（篝火 / 漂流瓶 / 交流会三模块共用）。
     * 规则：user.anonymous_name 存在且未超过 24 小时则复用；
     * 否则随机生成一个新名称，写回 user 表并续期 24 小时。
     *
     * @param userId 用户ID
     * @return 当前生效的匿名昵称
     */
    String getOrCreateAnonymousName(Long userId);

    /**
     * 根据身份模式解析对外展示名。
     * displayMode="anonymous"（或昵称为空）→ 统一匿名昵称；否则 → 用户自己的昵称。
     *
     * @param user        用户实体（避免重复查库）
     * @param displayMode 身份模式: nickname / anonymous
     * @return 对外展示名
     */
    String resolveDisplayName(User user, String displayMode);

    /**
     * 根据对外展示名解析头像（与 resolveDisplayName 的身份规则保持一致）。
     * 昵称身份 → 用户自定义头像（可能为 null，前端按 userId 显示系统默认头像）；
     * 匿名身份 → 一律返回 null，前端显示系统默认头像。
     *
     * @param user        用户实体
     * @param displayName 对外展示名（resolveDisplayName 的返回值）
     * @return 头像URL，匿名身份固定返回 null
     */
    String resolveAvatarForDisplayName(User user, String displayName);

    /**
     * 批量获取匿名昵称（漂流瓶列表等场景，避免 N+1）。
     * 对已过期/缺失的用户会逐个刷新名称并续期 24 小时。
     *
     * @param userIds 用户ID集合
     * @return userId → 匿名昵称
     */
    Map<Long, String> getAnonymousNameMap(Collection<Long> userIds);
}
