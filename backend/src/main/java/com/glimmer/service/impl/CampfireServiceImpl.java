package com.glimmer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.response.PageResult;
import com.glimmer.entity.Campfire;
import com.glimmer.entity.CampfireMember;
import com.glimmer.entity.CampfireMessage;
import com.glimmer.entity.TokenTransaction;
import com.glimmer.entity.User;
import com.glimmer.mapper.CampfireMapper;
import com.glimmer.mapper.CampfireMemberMapper;
import com.glimmer.mapper.CampfireMessageMapper;
import com.glimmer.mapper.TokenTransactionMapper;
import com.glimmer.mapper.UserMapper;
import com.glimmer.service.CampfireService;
import com.glimmer.service.UserService;
import com.glimmer.service.dto.CampfireMessageVO;
import com.glimmer.service.dto.CampfireVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 篝火服务实现
 * 见开发文档 §2.5 / §4.8
 */
@Slf4j
@Service
public class CampfireServiceImpl implements CampfireService {

    /** 篝火人数上限与代币消耗映射（10→1, 20→2, 30→3） */
    private static final Map<Integer, Integer> MAX_MEMBERS_TOKEN_COST = Map.of(10, 1, 20, 2, 30, 3);

    private final CampfireMapper campfireMapper;
    private final CampfireMemberMapper campfireMemberMapper;
    private final CampfireMessageMapper campfireMessageMapper;
    private final UserMapper userMapper;
    private final TokenTransactionMapper tokenTransactionMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserService userService;

    public CampfireServiceImpl(CampfireMapper campfireMapper,
                               CampfireMemberMapper campfireMemberMapper,
                               CampfireMessageMapper campfireMessageMapper,
                               UserMapper userMapper,
                               TokenTransactionMapper tokenTransactionMapper,
                               SimpMessagingTemplate messagingTemplate,
                               UserService userService) {
        this.campfireMapper = campfireMapper;
        this.campfireMemberMapper = campfireMemberMapper;
        this.campfireMessageMapper = campfireMessageMapper;
        this.userMapper = userMapper;
        this.tokenTransactionMapper = tokenTransactionMapper;
        this.messagingTemplate = messagingTemplate;
        this.userService = userService;
    }

    @Override
    public List<CampfireVO> getCampfireList(Long userId) {
        LambdaQueryWrapper<Campfire> wrapper = new LambdaQueryWrapper<Campfire>()
                .eq(Campfire::getStatus, "active")
                .orderByDesc(Campfire::getCreatedAt);
        List<Campfire> campfires = campfireMapper.selectList(wrapper);

        Map<Long, Long> memberCountMap = countMembers(campfires);

        return campfires.stream().map(c -> toVO(c, memberCountMap.getOrDefault(c.getId(), 0L)))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampfireVO createCampfire(Long userId, String name, int maxMembers) {
        // 1. 校验用户非 banned
        userService.checkUserNotMuted(userId);

        // 2. 校验 maxMembers ∈ {10, 20, 30}
        Integer tokenCost = MAX_MEMBERS_TOKEN_COST.get(maxMembers);
        if (tokenCost == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "人数上限仅支持 10/20/30");
        }

        // 3. 校验代币余额
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (user.getTokenBalance() == null || user.getTokenBalance() < tokenCost) {
            throw new BusinessException(ErrorCode.TOKEN_NOT_ENOUGH);
        }

        // 4. 扣代币（乐观锁 @Version）
        user.setTokenBalance(user.getTokenBalance() - tokenCost);
        boolean updated = userMapper.updateById(user) > 0;
        if (!updated) {
            throw new BusinessException(ErrorCode.CONFLICT, "代币扣减冲突，请重试");
        }

        // 5. 插入 campfire
        LocalDateTime now = LocalDateTime.now();
        Campfire campfire = new Campfire();
        campfire.setName(name);
        campfire.setType("custom");
        campfire.setMaxMembers(maxMembers);
        campfire.setCreatorId(userId);
        campfire.setStatus("active");
        campfire.setCreatedAt(now);
        campfire.setLastActiveAt(now);
        campfireMapper.insert(campfire);

        // 6. 创建者自动加入
        CampfireMember member = new CampfireMember();
        member.setCampfireId(campfire.getId());
        member.setUserId(userId);
        member.setJoinedAt(now);
        campfireMemberMapper.insert(member);

        // 7. 写流水：source='create_campfire'
        TokenTransaction tx = new TokenTransaction();
        tx.setUserId(userId);
        tx.setType("spend");
        tx.setAmount(tokenCost);
        tx.setSource("create_campfire");
        tx.setRefId(campfire.getId());
        tokenTransactionMapper.insert(tx);

        log.info("创建篝火成功: userId={}, campfireId={}, maxMembers={}, cost={}",
                userId, campfire.getId(), maxMembers, tokenCost);

        CampfireVO vo = toVO(campfire, 1L);
        return vo;
    }

    @Override
    public CampfireVO getCampfireDetail(Long userId, Long campfireId) {
        Campfire campfire = campfireMapper.selectById(campfireId);
        if (campfire == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "篝火不存在");
        }
        Long memberCount = countMembers(campfireId);
        return toVO(campfire, memberCount);
    }

    @Override
    public PageResult<CampfireMessageVO> getHistoryMessages(Long userId, Long campfireId, int page, int size) {
        Campfire campfire = campfireMapper.selectById(campfireId);
        if (campfire == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "篝火不存在");
        }
        // 校验用户是该篝火成员
        checkCampfireMember(userId, campfireId);

        Page<CampfireMessage> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<CampfireMessage> wrapper = new LambdaQueryWrapper<CampfireMessage>()
                .eq(CampfireMessage::getCampfireId, campfireId)
                .orderByAsc(CampfireMessage::getCreatedAt);
        IPage<CampfireMessage> result = campfireMessageMapper.selectPage(pageParam, wrapper);
        List<CampfireMessageVO> list = result.getRecords().stream()
                .map(this::toMessageVO).collect(Collectors.toList());
        return new PageResult<>(list, result.getTotal(), page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void joinCampfire(Long userId, Long campfireId) {
        Campfire campfire = campfireMapper.selectById(campfireId);
        if (campfire == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "篝火不存在");
        }
        // 校验状态 active
        if (!"active".equals(campfire.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "篝火不可加入");
        }
        // 校验未加入（已加入则静默返回成功）
        if (isMember(userId, campfireId)) {
            log.info("用户已加入篝火，静默处理: userId={}, campfireId={}", userId, campfireId);
            return;
        }
        // 校验人数未满
        Long memberCount = countMembers(campfireId);
        if (campfire.getMaxMembers() != null && memberCount >= campfire.getMaxMembers()) {
            throw new BusinessException(ErrorCode.CAMPFIRE_FULL);
        }
        // 插入成员
        CampfireMember member = new CampfireMember();
        member.setCampfireId(campfireId);
        member.setUserId(userId);
        member.setJoinedAt(LocalDateTime.now());
        campfireMemberMapper.insert(member);
        
        // 更新最后活跃时间
        campfire.setLastActiveAt(LocalDateTime.now());
        campfireMapper.updateById(campfire);
        
        log.info("加入篝火成功: userId={}, campfireId={}", userId, campfireId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void leaveCampfire(Long userId, Long campfireId) {
        Campfire campfire = campfireMapper.selectById(campfireId);
        if (campfire == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "篝火不存在");
        }
        // 删除成员
        int deleted = campfireMemberMapper.delete(new LambdaQueryWrapper<CampfireMember>()
                .eq(CampfireMember::getCampfireId, campfireId)
                .eq(CampfireMember::getUserId, userId));
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "未加入该篝火");
        }
        
        // 更新最后活跃时间
        campfire.setLastActiveAt(LocalDateTime.now());
        campfireMapper.updateById(campfire);
        
        log.info("退出篝火成功: userId={}, campfireId={}", userId, campfireId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void extinguishCampfire(Long userId, Long campfireId) {
        Campfire campfire = campfireMapper.selectById(campfireId);
        if (campfire == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "篝火不存在");
        }
        if (!userId.equals(campfire.getCreatorId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅创建者可熄灭篝火");
        }
        if ("default".equals(campfire.getType())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "系统默认篝火不可熄灭");
        }
        if (!"active".equals(campfire.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "篝火状态异常");
        }
        campfire.setStatus("extinguished");
        campfireMapper.updateById(campfire);
        campfireMemberMapper.delete(new LambdaQueryWrapper<CampfireMember>()
                .eq(CampfireMember::getCampfireId, campfireId));
        log.info("篝火已熄灭: userId={}, campfireId={}", userId, campfireId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CampfireMessageVO sendMessage(Long userId, Long campfireId, String content) {
        // 1. 校验用户非 banned
        userService.checkUserNotMuted(userId);
        // 2. 校验用户是该篝火成员
        checkCampfireMember(userId, campfireId);
        // 3. 获取用户信息
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        // 4. 插入消息（anonymous_name 冗余存储）
        LocalDateTime now = LocalDateTime.now();
        CampfireMessage message = new CampfireMessage();
        message.setCampfireId(campfireId);
        message.setUserId(userId);
        message.setAnonymousName(user.getAnonymousName());
        message.setContent(content);
        message.setCreatedAt(now);
        campfireMessageMapper.insert(message);

        CampfireMessageVO vo = toMessageVO(message);

        // 4. 通过 WebSocket 推送到 /topic/campfire/{campfireId}
        messagingTemplate.convertAndSend("/topic/campfire/" + campfireId, vo);

        log.info("篝火消息发送成功: userId={}, campfireId={}, messageId={}", userId, campfireId, message.getId());
        return vo;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 校验当前用户是该篝火成员
     */
    private void checkCampfireMember(Long userId, Long campfireId) {
        if (!isMember(userId, campfireId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "未加入该篝火");
        }
    }

    /**
     * 判断当前用户是否是该篝火成员
     */
    private boolean isMember(Long userId, Long campfireId) {
        Long count = campfireMemberMapper.selectCount(
                new LambdaQueryWrapper<CampfireMember>()
                        .eq(CampfireMember::getCampfireId, campfireId)
                        .eq(CampfireMember::getUserId, userId));
        return count != null && count > 0;
    }

    /**
     * 统计单个篝火成员数
     */
    private Long countMembers(Long campfireId) {
        return campfireMemberMapper.selectCount(new LambdaQueryWrapper<CampfireMember>()
                .eq(CampfireMember::getCampfireId, campfireId));
    }

    /**
     * 批量统计多个篝火成员数
     */
    private Map<Long, Long> countMembers(List<Campfire> campfires) {
        Map<Long, Long> result = new HashMap<>();
        if (campfires == null || campfires.isEmpty()) {
            return result;
        }
        List<Long> ids = campfires.stream().map(Campfire::getId).collect(Collectors.toList());
        List<CampfireMember> members = campfireMemberMapper.selectList(
                new LambdaQueryWrapper<CampfireMember>()
                        .in(CampfireMember::getCampfireId, ids));
        for (CampfireMember m : members) {
            result.merge(m.getCampfireId(), 1L, Long::sum);
        }
        return result;
    }

    private CampfireVO toVO(Campfire campfire, Long memberCount) {
        CampfireVO vo = new CampfireVO();
        vo.setId(campfire.getId());
        vo.setName(campfire.getName());
        vo.setType(campfire.getType());
        vo.setMaxMembers(campfire.getMaxMembers());
        vo.setCreatorId(campfire.getCreatorId());
        vo.setStatus(campfire.getStatus());
        vo.setCreatedAt(campfire.getCreatedAt());
        vo.setMemberCount(memberCount);
        return vo;
    }

    private CampfireMessageVO toMessageVO(CampfireMessage message) {
        CampfireMessageVO vo = new CampfireMessageVO();
        vo.setId(message.getId());
        vo.setCampfireId(message.getCampfireId());
        vo.setUserId(message.getUserId());
        vo.setAnonymousName(message.getAnonymousName());
        vo.setContent(message.getContent());
        vo.setCreatedAt(message.getCreatedAt());
        return vo;
    }
}
