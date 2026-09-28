package com.glimmer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.response.PageResult;
import com.glimmer.common.util.AnonymousNameGenerator;
import com.glimmer.entity.Flower;
import com.glimmer.entity.FlowerType;
import com.glimmer.entity.Punishment;
import com.glimmer.entity.User;
import com.glimmer.mapper.FlowerMapper;
import com.glimmer.mapper.FlowerTypeMapper;
import com.glimmer.mapper.UserMapper;
import com.glimmer.service.EchoService;
import com.glimmer.service.NotificationService;
import com.glimmer.service.PunishmentService;
import com.glimmer.service.UserService;
import com.glimmer.service.dto.ChangePasswordRequest;
import com.glimmer.service.dto.FlowerVO;
import com.glimmer.service.dto.GardenVO;
import com.glimmer.service.dto.UpdateNicknameRequest;
import com.glimmer.service.dto.UserAdminVO;
import com.glimmer.service.dto.UserProfileVO;
import com.glimmer.service.dto.UserVO;
import com.glimmer.service.util.GardenBrightnessHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户服务实现
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final FlowerMapper flowerMapper;
    private final FlowerTypeMapper flowerTypeMapper;
    private final NotificationService notificationService;
    private final PunishmentService punishmentService;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserMapper userMapper, FlowerMapper flowerMapper, FlowerTypeMapper flowerTypeMapper,
                           NotificationService notificationService, PunishmentService punishmentService,
                           PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.flowerMapper = flowerMapper;
        this.flowerTypeMapper = flowerTypeMapper;
        this.notificationService = notificationService;
        this.punishmentService = punishmentService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserVO getCurrentUserInfo(Long userId) {
        User user = getUserOrThrow(userId);
        return toUserVO(user);
    }

    @Override
    public void updateNickname(Long userId, UpdateNicknameRequest request) {
        User user = getUserOrThrow(userId);
        user.setNickname(request.getNickname());
        // 乐观锁更新（@Version）；若并发冲突返回 false
        boolean success = userMapper.updateById(user) > 0;
        if (!success) {
            throw new BusinessException(ErrorCode.CONFLICT, "昵称更新冲突，请重试");
        }
    }

    /** 头像URL最大长度（与 user.avatar_url 字段宽度一致） */
    private static final int AVATAR_URL_MAX_LENGTH = 500;

    @Override
    public void updateAvatar(Long userId, String avatarUrl) {
        getUserOrThrow(userId);
        String target;
        if (!StringUtils.hasText(avatarUrl)) {
            // 空值表示恢复系统默认头像
            target = null;
        } else {
            target = avatarUrl.trim();
            if (target.length() > AVATAR_URL_MAX_LENGTH) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "头像URL过长");
            }
            if (!target.startsWith("https://") && !target.startsWith("http://")) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "头像URL格式不正确");
            }
        }
        // updateById 默认忽略 null 字段，恢复默认头像需用 UpdateWrapper 显式置 NULL
        int updated = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getAvatarUrl, target));
        if (updated <= 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "头像更新冲突，请重试");
        }
        log.info("用户头像更新成功: userId={}, custom={}", userId, target != null);
    }

    /**
     * 修改密码：验证原密码 + 频率限制（一天一次）+ BCrypt 加密新密码
     */
    @Override
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getUserOrThrow(userId);

        // 1. 验证原密码（BCrypt matches）
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_NOT_MATCH);
        }

        // 2. 频率限制：一天只能修改一次
        if (user.getPasswordChangedAt() != null) {
            LocalDateTime lastChanged = user.getPasswordChangedAt();
            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
            if (lastChanged.plusDays(1).isAfter(now)) {
                throw new BusinessException(ErrorCode.PASSWORD_CHANGE_LIMITED);
            }
        }

        // 3. 加密并更新新密码，记录修改时间
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));

        boolean success = userMapper.updateById(user) > 0;
        if (!success) {
            throw new BusinessException(ErrorCode.CONFLICT, "密码更新冲突，请重试");
        }

        log.info("用户密码修改成功: userId={}", userId);
    }

    @Override
    public UserProfileVO getUserProfile(Long userId) {
        User user = getUserOrThrow(userId);
        UserProfileVO vo = new UserProfileVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAnonymousName(user.getAnonymousName());
        vo.setTotalFirefly(user.getTotalFirefly());
        vo.setBrightnessLevel(GardenBrightnessHelper.calculateLevel(user.getTotalFirefly()));
        return vo;
    }

    @Override
    public GardenVO getUserGarden(Long userId) {
        User user = getUserOrThrow(userId);
        GardenVO vo = new GardenVO();
        vo.setUserId(user.getId());
        vo.setTotalFirefly(user.getTotalFirefly());
        vo.setFireflyBalance(user.getFireflyBalance());
        vo.setBrightnessLevel(GardenBrightnessHelper.calculateLevel(user.getTotalFirefly()));

        // 查询用户的花朵列表
        List<Flower> flowers = flowerMapper.selectList(new LambdaQueryWrapper<Flower>()
                .eq(Flower::getUserId, userId)
                .orderByDesc(Flower::getPlantedAt));
        vo.setFlowers(convertFlowers(flowers));
        return vo;
    }

    private List<FlowerVO> convertFlowers(List<Flower> flowers) {
        if (flowers.isEmpty()) {
            return Collections.emptyList();
        }
        // 批量查询花种名称
        Set<Long> typeIds = flowers.stream().map(Flower::getFlowerTypeId).collect(Collectors.toSet());
        Map<Long, String> typeNameMap = flowerTypeMapper.selectBatchIds(typeIds).stream()
                .collect(Collectors.toMap(FlowerType::getId, FlowerType::getName, (a, b) -> a));

        return flowers.stream().map(f -> {
            FlowerVO vo = new FlowerVO();
            vo.setId(f.getId());
            vo.setFlowerTypeId(f.getFlowerTypeId());
            vo.setFlowerTypeName(typeNameMap.get(f.getFlowerTypeId()));
            vo.setStage(f.getStage());
            vo.setStageWaterCount(f.getStageWaterCount());
            vo.setPlantedAt(f.getPlantedAt());
            vo.setLastWaterAt(f.getLastWaterAt());
            vo.setBloomedAt(f.getBloomedAt());
            return vo;
        }).collect(Collectors.toList());
    }

    private User getUserOrThrow(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    @Override
    public void checkUserNotMuted(Long userId) {
        User user = getUserOrThrow(userId);
        // 发言限制以 punishment 表中"生效中的非警告处罚"为准：
        // 系统自动 BAN 的用户 user.status 仍为 active，但一样不能发言；
        // 被 MUTE 禁言的用户返回禁言提示，BAN 用户返回封禁提示（均附申诉邮箱）。
        if (punishmentService.isUserBanned(userId)) {
            Punishment latest = punishmentService.getLatestActiveByUserId(userId);
            if (latest != null && Punishment.TYPE_BAN.equals(latest.getType())) {
                throw new BusinessException(ErrorCode.USER_BANNED);
            }
            throw new BusinessException(ErrorCode.USER_MUTED);
        }
        // 兜底：status 残留 banned 但已无生效处罚（解禁后历史状态），自动恢复为 active
        if ("banned".equals(user.getStatus())) {
            user.setStatus("active");
            userMapper.updateById(user);
            log.info("用户处罚已结束，自动恢复为active: userId={}", userId);
        }
    }

    @Override
    public String getAiContext(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        return user.getAiContext();
    }

    /** 匿名昵称有效期：24 小时（篝火/漂流瓶/交流会全模块共用同一个名称） */
    private static final long ANONYMOUS_NAME_TTL_HOURS = 24L;

    @Override
    public String getOrCreateAnonymousName(Long userId) {
        User user = getUserOrThrow(userId);
        LocalDateTime now = LocalDateTime.now();
        // 名称存在且未过期：直接复用，保证用户 24 小时内在所有匿名场景身份一致
        if (StringUtils.hasText(user.getAnonymousName())
                && user.getAnonymousNameExpiresAt() != null
                && user.getAnonymousNameExpiresAt().isAfter(now)) {
            return user.getAnonymousName();
        }
        // 不存在或已过期：重新随机生成并续期 24 小时
        String newName = AnonymousNameGenerator.generateRandom();
        User update = new User();
        update.setId(userId);
        update.setAnonymousName(newName);
        update.setAnonymousNameExpiresAt(now.plusHours(ANONYMOUS_NAME_TTL_HOURS));
        userMapper.updateById(update);
        log.info("刷新用户匿名昵称: userId={}, anonymousName={}", userId, newName);
        return newName;
    }

    @Override
    public String resolveDisplayName(User user, String displayMode) {
        // 用户显式选择昵称且昵称非空时展示真实昵称；匿名模式（或昵称为空）走统一匿名昵称
        if (user == null) {
            return "匿名旅人";
        }
        if (!"anonymous".equalsIgnoreCase(displayMode) && StringUtils.hasText(user.getNickname())) {
            return user.getNickname();
        }
        return getOrCreateAnonymousName(user.getId());
    }

    @Override
    public String resolveAvatarForDisplayName(User user, String displayName) {
        // 展示名等于昵称 → 昵称身份，返回自定义头像（无自定义则前端按 userId 显示系统默认头像）；
        // 匿名身份（展示名不等于昵称）→ 固定返回 null，前端显示系统默认头像
        if (user == null || !StringUtils.hasText(user.getNickname())
                || !user.getNickname().equals(displayName)) {
            return null;
        }
        return StringUtils.hasText(user.getAvatarUrl()) ? user.getAvatarUrl() : null;
    }

    @Override
    public Map<Long, String> getAnonymousNameMap(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // 去重后一次性查询，避免列表场景 N+1
        List<Long> distinctIds = userIds.stream().distinct().collect(Collectors.toList());
        List<User> users = userMapper.selectBatchIds(distinctIds);
        LocalDateTime now = LocalDateTime.now();
        Map<Long, String> result = new HashMap<>(users.size() * 2);
        for (User user : users) {
            String name;
            if (StringUtils.hasText(user.getAnonymousName())
                    && user.getAnonymousNameExpiresAt() != null
                    && user.getAnonymousNameExpiresAt().isAfter(now)) {
                name = user.getAnonymousName();
            } else {
                // 过期/缺失：刷新并续期
                name = AnonymousNameGenerator.generateRandom();
                User update = new User();
                update.setId(user.getId());
                update.setAnonymousName(name);
                update.setAnonymousNameExpiresAt(now.plusHours(ANONYMOUS_NAME_TTL_HOURS));
                userMapper.updateById(update);
                log.info("批量刷新用户匿名昵称: userId={}, anonymousName={}", user.getId(), name);
            }
            result.put(user.getId(), name);
        }
        return result;
    }

    @Override
    public PageResult<UserAdminVO> getUserListForAdmin(String status, String role, int page, int size) {
        Page<User> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(StringUtils.hasText(status), User::getStatus, status)
                .eq(StringUtils.hasText(role), User::getRole, role)
                .orderByDesc(User::getCreatedAt);

        IPage<User> result = userMapper.selectPage(pageParam, wrapper);
        List<UserAdminVO> list = result.getRecords().stream()
                .map(this::toAdminVO)
                .collect(Collectors.toList());
        return new PageResult<>(list, result.getTotal(), page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserStatus(Long adminId, Long userId, String status) {
        // 1. 校验 status 取值
        if (!"active".equals(status) && !"banned".equals(status)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "状态只能为 active 或 banned");
        }

        // 2. 不允许修改自己的状态
        if (adminId.equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不允许修改自己的状态");
        }

        // 3. 查询目标用户
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }

        // 4. 不允许封禁其他管理员
        if ("banned".equals(status) && "admin".equals(user.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不允许封禁管理员");
        }

        // 4.5 不允许封禁 AI 机器人账号（回音）
        if ("banned".equals(status) && (EchoService.BOT_USERNAME.equals(user.getUsername())
                || EchoService.ROLE_BOT.equals(user.getRole()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不允许封禁 AI 机器人账号");
        }

        // 5. 状态未变化直接返回
        if (status.equals(user.getStatus())) {
            return;
        }

        // 6. 如果是封禁操作，创建BAN处罚单（会自动同步用户状态为banned）
        if ("banned".equals(status)) {
            punishmentService.createPunishment(userId, Punishment.TYPE_BAN,
                    "管理员手动封禁", Punishment.SOURCE_ADMIN, null);
        }

        // 7. 如果是解禁操作，撤销该用户所有限制发言的生效罚单（BAN、MUTE_24H、MUTE_7D）
        if ("active".equals(status)) {
            List<Punishment> activePunishments = punishmentService.getActiveByUserId(userId);
            for (Punishment p : activePunishments) {
                if (!Punishment.TYPE_WARNING.equals(p.getType())) {
                    punishmentService.revokePunishment(p.getId());
                    log.info("解禁时撤销罚单: punishmentId={}, userId={}, type={}", p.getId(), userId, p.getType());
                }
            }
        }

        // 8. 发送 system 通知
        if ("banned".equals(status)) {
            notificationService.sendNotification(
                    userId, "system", "账号已被管理员封禁",
                    "您的账号已被管理员封禁，无法登录；如有异议请联系管理员申诉：1623919525@qq.com",
                    null, null);
        } else {
            notificationService.sendNotification(
                    userId, "system", "账号已被管理员解封",
                    "您的账号已被管理员解封，欢迎回来。",
                    null, null);
        }
        log.info("管理员更新用户状态: adminId={}, userId={}, status={}", adminId, userId, status);
    }

    private UserAdminVO toAdminVO(User user) {
        UserAdminVO vo = new UserAdminVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAnonymousName(user.getAnonymousName());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setTokenBalance(user.getTokenBalance());
        vo.setTotalFirefly(user.getTotalFirefly());
        vo.setFireflyBalance(user.getFireflyBalance());
        vo.setTotalSignDays(user.getTotalSignDays());
        vo.setPendingReportCount(user.getPendingReportCount());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }

    private UserVO toUserVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        // UID = 10000 + id，对外账号，替代 username 展示
        vo.setUid(10000L + user.getId());
        vo.setUsername(user.getUsername());
        // 手机号必须脱敏回填，否则前端永远拿不到 phone，MyView 会误判为"未绑定"
        vo.setPhone(maskPhone(user.getPhone()));
        vo.setNickname(user.getNickname());
        // 头像URL必须回填，否则前端永远拿不到自定义头像，UserAvatar 会一直显示系统默认头像
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setAnonymousName(user.getAnonymousName());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setTokenBalance(user.getTokenBalance());
        vo.setTotalFirefly(user.getTotalFirefly());
        vo.setFireflyBalance(user.getFireflyBalance());
        vo.setTotalSignDays(user.getTotalSignDays());
        
        // 从punishment表获取当前生效的处罚信息
        Punishment currentPunishment = punishmentService.getLatestActiveByUserId(user.getId());
        if (currentPunishment != null) {
            vo.setMuteType(convertToLowerCaseType(currentPunishment.getType()));
            vo.setMuteEndTime(currentPunishment.getEndAt());
        } else {
            vo.setMuteType(null);
            vo.setMuteEndTime(null);
        }
        return vo;
    }
    
    private String convertToLowerCaseType(String punishmentType) {
        switch (punishmentType) {
            case Punishment.TYPE_WARNING: return "warning";
            case Punishment.TYPE_MUTE_24H: return "mute_24h";
            case Punishment.TYPE_MUTE_7D: return "mute_7d";
            case Punishment.TYPE_BAN: return "ban";
            default: return punishmentType != null ? punishmentType.toLowerCase() : null;
        }
    }

    /**
     * 手机号脱敏：138****5678
     * 与 AuthServiceImpl.maskPhone 同逻辑，避免跨类依赖，单独维护一份
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
