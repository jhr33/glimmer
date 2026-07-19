package com.glimmer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.entity.Flower;
import com.glimmer.entity.FlowerType;
import com.glimmer.entity.User;
import com.glimmer.mapper.FlowerMapper;
import com.glimmer.mapper.FlowerTypeMapper;
import com.glimmer.mapper.UserMapper;
import com.glimmer.service.FlowerService;
import com.glimmer.service.dto.FlowerTypeVO;
import com.glimmer.service.dto.FlowerVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 花园养成服务实现
 * 见开发文档 §2.7 / §4.10
 */
@Slf4j
@Service
public class FlowerServiceImpl implements FlowerService {

    /** 每次浇水消耗的萤火余额 */
    private static final int WATER_COST = 2;

    private final FlowerMapper flowerMapper;
    private final FlowerTypeMapper flowerTypeMapper;
    private final UserMapper userMapper;

    public FlowerServiceImpl(FlowerMapper flowerMapper, FlowerTypeMapper flowerTypeMapper, UserMapper userMapper) {
        this.flowerMapper = flowerMapper;
        this.flowerTypeMapper = flowerTypeMapper;
        this.userMapper = userMapper;
    }

    @Override
    public List<FlowerTypeVO> getFlowerTypeList() {
        List<FlowerType> types = flowerTypeMapper.selectList(
                new LambdaQueryWrapper<FlowerType>()
                        .eq(FlowerType::getAvailable, 1)
                        .orderByAsc(FlowerType::getId));
        return types.stream().map(this::toTypeVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowerVO redeemFlower(Long userId, Long flowerTypeId) {
        // 1. 校验花种存在且 available=1
        FlowerType flowerType = flowerTypeMapper.selectById(flowerTypeId);
        if (flowerType == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "花种不存在");
        }
        if (flowerType.getAvailable() == null || flowerType.getAvailable() != 1) {
            throw new BusinessException(ErrorCode.FLOWER_TYPE_UNAVAILABLE);
        }

        // 2. 校验 user.total_firefly >= required_firefly
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        int requiredFirefly = flowerType.getRequiredFirefly() == null ? 0 : flowerType.getRequiredFirefly();
        int totalFirefly = user.getTotalFirefly() == null ? 0 : user.getTotalFirefly();
        if (totalFirefly < requiredFirefly) {
            throw new BusinessException(ErrorCode.FIREFLY_TOTAL_NOT_ENOUGH);
        }

        // 3. 校验 user.firefly_balance >= redeem_firefly
        int redeemFirefly = flowerType.getRedeemFirefly() == null ? 0 : flowerType.getRedeemFirefly();
        int fireflyBalance = user.getFireflyBalance() == null ? 0 : user.getFireflyBalance();
        if (fireflyBalance < redeemFirefly) {
            throw new BusinessException(ErrorCode.FIREFLY_BALANCE_NOT_ENOUGH);
        }

        // 4. 扣萤火余额（乐观锁 @Version）
        user.setFireflyBalance(fireflyBalance - redeemFirefly);
        boolean updated = userMapper.updateById(user) > 0;
        if (!updated) {
            throw new BusinessException(ErrorCode.CONFLICT, "萤火扣减冲突，请重试");
        }

        // 5. 插入 flower
        LocalDateTime now = LocalDateTime.now();
        Flower flower = new Flower();
        flower.setUserId(userId);
        flower.setFlowerTypeId(flowerTypeId);
        flower.setStage("seed");
        flower.setStageWaterCount(0);
        flower.setPlantedAt(now);
        flowerMapper.insert(flower);

        log.info("兑换花种成功: userId={}, flowerId={}, flowerTypeId={}", userId, flower.getId(), flowerTypeId);
        return toVO(flower, flowerType);
    }

    @Override
    public List<FlowerVO> getMyFlowers(Long userId) {
        List<Flower> flowers = flowerMapper.selectList(
                new LambdaQueryWrapper<Flower>()
                        .eq(Flower::getUserId, userId)
                        .orderByDesc(Flower::getPlantedAt));
        if (flowers.isEmpty()) {
            return List.of();
        }
        // 批量查询花种
        Set<Long> typeIds = flowers.stream().map(Flower::getFlowerTypeId).collect(Collectors.toSet());
        Map<Long, FlowerType> typeMap = flowerTypeMapper.selectBatchIds(typeIds).stream()
                .collect(Collectors.toMap(FlowerType::getId, t -> t, (a, b) -> a));
        return flowers.stream().map(f -> toVO(f, typeMap.get(f.getFlowerTypeId()))).collect(Collectors.toList());
    }

    @Override
    public FlowerVO getFlowerDetail(Long userId, Long flowerId) {
        Flower flower = checkFlowerOwner(userId, flowerId);
        FlowerType flowerType = flowerTypeMapper.selectById(flower.getFlowerTypeId());
        return toVO(flower, flowerType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowerVO waterFlower(Long userId, Long flowerId) {
        // 1. 校验花朵属于当前用户
        Flower flower = checkFlowerOwner(userId, flowerId);
        FlowerType flowerType = flowerTypeMapper.selectById(flower.getFlowerTypeId());

        // 2. 校验当前阶段非 bloom
        if ("bloom".equals(flower.getStage())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "花朵已开花，无需浇水");
        }

        // 3. 校验并扣除萤火余额（每次浇水消耗 WATER_COST 萤火，不限制每日次数）
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        int fireflyBalance = user.getFireflyBalance() == null ? 0 : user.getFireflyBalance();
        if (fireflyBalance < WATER_COST) {
            throw new BusinessException(ErrorCode.FIREFLY_BALANCE_NOT_ENOUGH);
        }
        user.setFireflyBalance(fireflyBalance - WATER_COST);
        boolean updated = userMapper.updateById(user) > 0;
        if (!updated) {
            throw new BusinessException(ErrorCode.CONFLICT, "萤火扣减冲突，请重试");
        }

        // 4. stage_water_count += 1, last_water_at = now()
        int currentCount = (flower.getStageWaterCount() == null ? 0 : flower.getStageWaterCount()) + 1;
        LocalDateTime now = LocalDateTime.now();
        String currentStage = flower.getStage();
        int threshold = getStageThreshold(flowerType, currentStage);

        // 5. 判断是否达到阈值推进阶段
        if (threshold > 0 && currentCount >= threshold) {
            // 推进到下一阶段，stage_water_count 归零
            String nextStage = getNextStage(currentStage);
            flower.setStage(nextStage);
            flower.setStageWaterCount(0);
            if ("bloom".equals(nextStage)) {
                flower.setBloomedAt(now);
            }
        } else {
            flower.setStageWaterCount(currentCount);
        }
        flower.setLastWaterAt(now);

        // 持久化更新
        flowerMapper.updateById(flower);
        log.info("浇水成功: userId={}, flowerId={}, stage={}, stageWaterCount={}, cost={}萤火",
                userId, flowerId, flower.getStage(), flower.getStageWaterCount(), WATER_COST);

        return toVO(flower, flowerType);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 校验花朵属于当前用户
     */
    private Flower checkFlowerOwner(Long userId, Long flowerId) {
        Flower flower = flowerMapper.selectById(flowerId);
        if (flower == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "花朵不存在");
        }
        if (!userId.equals(flower.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该花朵");
        }
        return flower;
    }

    /**
     * 获取当前阶段浇水阈值
     */
    private int getStageThreshold(FlowerType type, String stage) {
        if (type == null) {
            return 0;
        }
        switch (stage) {
            case "seed":
                return type.getSeedToSprout() == null ? 0 : type.getSeedToSprout();
            case "sprout":
                return type.getSproutToSeedling() == null ? 0 : type.getSproutToSeedling();
            case "seedling":
                return type.getSeedlingToBud() == null ? 0 : type.getSeedlingToBud();
            case "bud":
                return type.getBudToBloom() == null ? 0 : type.getBudToBloom();
            case "bloom":
            default:
                return 0;
        }
    }

    /**
     * 获取下一阶段
     */
    private String getNextStage(String stage) {
        switch (stage) {
            case "seed":
                return "sprout";
            case "sprout":
                return "seedling";
            case "seedling":
                return "bud";
            case "bud":
                return "bloom";
            case "bloom":
            default:
                return "bloom";
        }
    }

    /**
     * 计算进度百分比
     */
    private int calcProgressPercent(String stage, int stageWaterCount, int threshold) {
        if ("bloom".equals(stage) || threshold <= 0) {
            return 100;
        }
        int percent = (int) ((long) stageWaterCount * 100 / threshold);
        return Math.min(percent, 100);
    }

    private FlowerTypeVO toTypeVO(FlowerType type) {
        FlowerTypeVO vo = new FlowerTypeVO();
        vo.setId(type.getId());
        vo.setName(type.getName());
        vo.setDescription(type.getDescription());
        vo.setRedeemFirefly(type.getRedeemFirefly());
        vo.setRequiredFirefly(type.getRequiredFirefly());
        vo.setAvailable(type.getAvailable());
        vo.setIconSeed(type.getIconSeed());
        vo.setIconSprout(type.getIconSprout());
        vo.setIconSeedling(type.getIconSeedling());
        vo.setIconBud(type.getIconBud());
        vo.setIconBloom(type.getIconBloom());
        vo.setCreatedAt(type.getCreatedAt());
        return vo;
    }

    private FlowerVO toVO(Flower flower, FlowerType flowerType) {
        FlowerVO vo = new FlowerVO();
        vo.setId(flower.getId());
        vo.setUserId(flower.getUserId());
        vo.setFlowerTypeId(flower.getFlowerTypeId());
        vo.setFlowerTypeName(flowerType == null ? null : flowerType.getName());
        vo.setStage(flower.getStage());
        vo.setStageWaterCount(flower.getStageWaterCount());
        vo.setPlantedAt(flower.getPlantedAt());
        vo.setLastWaterAt(flower.getLastWaterAt());
        vo.setBloomedAt(flower.getBloomedAt());
        int threshold = getStageThreshold(flowerType, flower.getStage());
        vo.setCurrentStageThreshold(threshold);
        int waterCount = flower.getStageWaterCount() == null ? 0 : flower.getStageWaterCount();
        vo.setProgressPercent(calcProgressPercent(flower.getStage(), waterCount, threshold));
        return vo;
    }
}
