package com.glimmer.service;

import com.glimmer.entity.Punishment;

import java.util.List;

/**
 * 处罚单服务接口
 */
public interface PunishmentService {

    /**
     * 创建处罚单
     * @param userId 被处罚用户ID
     * @param type 处罚类型
     * @param reason 处罚原因
     * @param sourceType 来源类型
     * @param sourceId 来源ID
     * @return 创建的处罚单
     */
    Punishment createPunishment(Long userId, String type, String reason, String sourceType, Long sourceId);

    /**
     * 根据ID获取处罚单
     * @param id 处罚单ID
     * @return 处罚单
     */
    Punishment getById(Long id);

    /**
     * 查询用户当前生效的处罚列表
     * @param userId 用户ID
     * @return 生效的处罚列表
     */
    List<Punishment> getActiveByUserId(Long userId);

    /**
     * 查询用户当前生效的处罚（最新一条）
     * @param userId 用户ID
     * @return 最新生效的处罚
     */
    Punishment getLatestActiveByUserId(Long userId);

    /**
     * 根据来源查询处罚记录
     * @param sourceId 来源ID
     * @param sourceType 来源类型
     * @return 处罚记录列表
     */
    List<Punishment> getBySource(Long sourceId, String sourceType);

    /**
     * 撤销处罚
     * @param id 处罚单ID
     */
    void revokePunishment(Long id);

    /**
     * 过期处罚（定时任务调用）
     */
    void expirePunishments();

    /**
     * 检查用户是否被封禁（有生效的BAN或MUTE处罚）
     * 注意：此方法用于限制发言等写操作，不用于判断是否可登录
     * @param userId 用户ID
     * @return 是否被封禁
     */
    boolean isUserBanned(Long userId);

    /**
     * 检查用户是否被永久封禁（有生效的BAN处罚）
     * 仅 BAN 类型才拒绝登录，MUTE 类型允许登录但限制发言
     * @param userId 用户ID
     * @return 是否被永久封禁
     */
    boolean isUserPermanentlyBanned(Long userId);

    /**
     * 获取用户当前处罚类型
     * @param userId 用户ID
     * @return 处罚类型（WARNING/MUTE_24H/MUTE_7D/BAN/null）
     */
    String getUserCurrentPunishmentType(Long userId);
}