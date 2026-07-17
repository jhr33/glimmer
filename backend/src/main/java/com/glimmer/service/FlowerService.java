package com.glimmer.service;

import com.glimmer.service.dto.FlowerTypeVO;
import com.glimmer.service.dto.FlowerVO;

import java.util.List;

/**
 * 花园养成服务接口
 * 见开发文档 §2.7 / §4.10
 */
public interface FlowerService {

    /**
     * 花种列表（仅 available=1）
     */
    List<FlowerTypeVO> getFlowerTypeList();

    /**
     * 兑换花种（扣萤火余额）
     */
    FlowerVO redeemFlower(Long userId, Long flowerTypeId);

    /**
     * 我的花朵列表
     */
    List<FlowerVO> getMyFlowers(Long userId);

    /**
     * 花朵详情（含当前阶段、进度）
     */
    FlowerVO getFlowerDetail(Long userId, Long flowerId);

    /**
     * 浇水（每日1次）
     */
    FlowerVO waterFlower(Long userId, Long flowerId);
}
