package com.glimmer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.response.PageResult;
import com.glimmer.entity.Flower;
import com.glimmer.entity.FlowerType;
import com.glimmer.entity.User;
import com.glimmer.mapper.FlowerMapper;
import com.glimmer.mapper.FlowerTypeMapper;
import com.glimmer.mapper.UserMapper;
import com.glimmer.service.NotificationService;
import com.glimmer.service.UserService;
import com.glimmer.service.dto.FlowerVO;
import com.glimmer.service.dto.GardenVO;
import com.glimmer.service.dto.UpdateNicknameRequest;
import com.glimmer.service.dto.UserAdminVO;
import com.glimmer.service.dto.UserProfileVO;
import com.glimmer.service.dto.UserVO;
import com.glimmer.service.util.GardenBrightnessHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
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

    public UserServiceImpl(UserMapper userMapper, FlowerMapper flowerMapper, FlowerTypeMapper flowerTypeMapper,
                           NotificationService notificationService) {
        this.userMapper = userMapper;
        this.flowerMapper = flowerMapper;
        this.flowerTypeMapper = flowerTypeMapper;
        this.notificationService = notificationService;
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

        // 5. 状态未变化直接返回
        if (status.equals(user.getStatus())) {
            return;
        }

        // 6. 更新状态（乐观锁 @Version）
        user.setStatus(status);
        boolean updated = userMapper.updateById(user) > 0;
        if (!updated) {
            throw new BusinessException(ErrorCode.CONFLICT, "状态更新冲突，请重试");
        }

        // 7. 发送 system 通知
        if ("banned".equals(status)) {
            notificationService.sendNotification(
                    userId, "system", "账号已被管理员封禁",
                    "您的账号已被管理员封禁，如有疑问请联系管理员申诉。",
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
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAnonymousName(user.getAnonymousName());
        vo.setRole(user.getRole());
        vo.setTokenBalance(user.getTokenBalance());
        vo.setTotalFirefly(user.getTotalFirefly());
        vo.setFireflyBalance(user.getFireflyBalance());
        vo.setTotalSignDays(user.getTotalSignDays());
        return vo;
    }
}
