package com.glimmer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.response.PageResult;
import com.glimmer.entity.DriftBottle;
import com.glimmer.entity.DriftBottlePickRecord;
import com.glimmer.entity.DriftBottleReply;
import com.glimmer.entity.Letter;
import com.glimmer.entity.TokenTransaction;
import com.glimmer.entity.User;
import com.glimmer.mapper.DriftBottleMapper;
import com.glimmer.mapper.DriftBottlePickRecordMapper;
import com.glimmer.mapper.DriftBottleReplyMapper;
import com.glimmer.mapper.LetterMapper;
import com.glimmer.mapper.TokenTransactionMapper;
import com.glimmer.mapper.UserMapper;
import com.glimmer.service.LetterService;
import com.glimmer.service.UserService;
import com.glimmer.service.dto.LetterVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 信件服务实现
 * 见开发文档 §2.4
 */
@Slf4j
@Service
public class LetterServiceImpl implements LetterService {

    private final LetterMapper letterMapper;
    private final UserMapper userMapper;
    private final TokenTransactionMapper tokenTransactionMapper;
    private final DriftBottleMapper driftBottleMapper;
    private final DriftBottleReplyMapper driftBottleReplyMapper;
    private final DriftBottlePickRecordMapper driftBottlePickRecordMapper;
    private final ObjectMapper objectMapper;
    private final UserService userService;

    public LetterServiceImpl(LetterMapper letterMapper,
                             UserMapper userMapper,
                             TokenTransactionMapper tokenTransactionMapper,
                             DriftBottleMapper driftBottleMapper,
                             DriftBottleReplyMapper driftBottleReplyMapper,
                             DriftBottlePickRecordMapper driftBottlePickRecordMapper,
                             ObjectMapper objectMapper,
                             UserService userService) {
        this.letterMapper = letterMapper;
        this.userMapper = userMapper;
        this.tokenTransactionMapper = tokenTransactionMapper;
        this.driftBottleMapper = driftBottleMapper;
        this.driftBottleReplyMapper = driftBottleReplyMapper;
        this.driftBottlePickRecordMapper = driftBottlePickRecordMapper;
        this.objectMapper = objectMapper;
        this.userService = userService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void writeLetter(Long senderId, Long receiverId, String content, Long sourceBottleReplyId) {
        // 校验发送者非 banned
        userService.checkUserNotMuted(senderId);

        // 校验代币余额 >= 1
        User sender = userMapper.selectById(senderId);
        if (sender == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (sender.getTokenBalance() == null || sender.getTokenBalance() < 1) {
            throw new BusinessException(ErrorCode.TOKEN_NOT_ENOUGH);
        }

        // 校验接收者存在
        User receiver = userMapper.selectById(receiverId);
        if (receiver == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "收信人不存在");
        }

        // 校验 sourceBottleReplyId 对应的回复存在
        DriftBottleReply reply = driftBottleReplyMapper.selectById(sourceBottleReplyId);
        if (reply == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "来源回复不存在");
        }

        // 校验发送者确实捡到过对应瓶子 OR 发送者是瓶主
        // 瓶主可以给回复者写信，捡瓶者也可以给回复者写信
        DriftBottle bottle = driftBottleMapper.selectById(reply.getBottleId());
        if (bottle == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "漂流瓶不存在");
        }
        boolean isOwner = senderId.equals(bottle.getUserId());
        Long pickCount = driftBottlePickRecordMapper.selectCount(
                new LambdaQueryWrapper<DriftBottlePickRecord>()
                        .eq(DriftBottlePickRecord::getBottleId, reply.getBottleId())
                        .eq(DriftBottlePickRecord::getUserId, senderId));
        boolean isPicker = pickCount != null && pickCount > 0;
        if (!isOwner && !isPicker) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "未捡到该漂流瓶，无法写信");
        }

        // 校验同一回复只能写一封信
        Long existingLetterCount = letterMapper.selectCount(
                new LambdaQueryWrapper<Letter>()
                        .eq(Letter::getSourceType, "bottle_reply")
                        .eq(Letter::getSourceId, sourceBottleReplyId));
        if (existingLetterCount != null && existingLetterCount > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "该回复已收到信件，无法重复发送");
        }

        // 插入 letter
        Letter letter = new Letter();
        letter.setSenderId(senderId);
        letter.setReceiverId(receiverId);
        letter.setParentId(null);
        letter.setSourceType("bottle_reply");
        letter.setSourceId(sourceBottleReplyId);
        letter.setContent(content);
        letter.setIsReplied(0);
        letterMapper.insert(letter);

        // 扣代币 +1（乐观锁）
        sender.setTokenBalance(sender.getTokenBalance() - 1);
        boolean updated = userMapper.updateById(sender) > 0;
        if (!updated) {
            throw new BusinessException(ErrorCode.CONFLICT, "代币扣减冲突，请重试");
        }

        // 写流水：type=spend, source=write_letter, amount=1, ref_id=letter.id
        TokenTransaction tx = new TokenTransaction();
        tx.setUserId(senderId);
        tx.setType("spend");
        tx.setAmount(1);
        tx.setSource("write_letter");
        tx.setRefId(letter.getId());
        tokenTransactionMapper.insert(tx);

        log.info("信件发送成功: senderId={}, receiverId={}, letterId={}", senderId, receiverId, letter.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replyLetter(Long userId, Long letterId, String content) {
        userService.checkUserNotMuted(userId);

        Letter original = letterMapper.selectById(letterId);
        if (original == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "信件不存在");
        }
        // 校验当前用户是 letter.receiver_id
        if (!userId.equals(original.getReceiverId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅收信人可回复信件");
        }
        // 校验 letter.is_replied == 0
        if (original.getIsReplied() != null && original.getIsReplied() == 1) {
            throw new BusinessException(ErrorCode.LETTER_REPLIED);
        }

        // 校验代币余额 >= 1
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (user.getTokenBalance() == null || user.getTokenBalance() < 1) {
            throw new BusinessException(ErrorCode.TOKEN_NOT_ENOUGH);
        }

        // 插入新 letter
        Letter reply = new Letter();
        reply.setSenderId(userId);
        reply.setReceiverId(original.getSenderId());
        reply.setParentId(letterId);
        reply.setSourceType(original.getSourceType());
        reply.setSourceId(original.getSourceId());
        reply.setContent(content);
        reply.setIsReplied(0);
        reply.setIsRead(0);
        letterMapper.insert(reply);

        // 更新原 letter.is_replied=1
        letterMapper.update(null, new LambdaUpdateWrapper<Letter>()
                .eq(Letter::getId, letterId)
                .set(Letter::getIsReplied, 1));

        // 扣代币（乐观锁）
        user.setTokenBalance(user.getTokenBalance() - 1);
        boolean updated = userMapper.updateById(user) > 0;
        if (!updated) {
            throw new BusinessException(ErrorCode.CONFLICT, "代币扣减冲突，请重试");
        }

        // 写流水：type=spend, source=reply_letter, amount=1, ref_id=reply.id
        TokenTransaction tx = new TokenTransaction();
        tx.setUserId(userId);
        tx.setType("spend");
        tx.setAmount(1);
        tx.setSource("reply_letter");
        tx.setRefId(reply.getId());
        tokenTransactionMapper.insert(tx);

        log.info("信件回复成功: userId={}, letterId={}, replyLetterId={}", userId, letterId, reply.getId());
    }

    @Override
    public PageResult<LetterVO> getInbox(Long userId, int page, int size) {
        Page<Letter> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Letter> wrapper = new LambdaQueryWrapper<Letter>()
                .eq(Letter::getReceiverId, userId)
                .orderByDesc(Letter::getIsRead)
                .orderByDesc(Letter::getCreatedAt);
        IPage<Letter> result = letterMapper.selectPage(pageParam, wrapper);
        List<LetterVO> list = toVOList(result.getRecords());
        return new PageResult<>(list, result.getTotal(), page, size);
    }

    @Override
    public PageResult<LetterVO> getSent(Long userId, int page, int size) {
        Page<Letter> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Letter> wrapper = new LambdaQueryWrapper<Letter>()
                .eq(Letter::getSenderId, userId)
                .orderByDesc(Letter::getCreatedAt);
        IPage<Letter> result = letterMapper.selectPage(pageParam, wrapper);
        List<LetterVO> list = toVOList(result.getRecords());
        return new PageResult<>(list, result.getTotal(), page, size);
    }

    @Override
    public LetterVO getLetterDetail(Long userId, Long letterId) {
        Letter letter = letterMapper.selectById(letterId);
        if (letter == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "信件不存在");
        }
        if (!userId.equals(letter.getSenderId()) && !userId.equals(letter.getReceiverId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该信件");
        }
        LetterVO vo = toVO(letter);
        User sender = userMapper.selectById(letter.getSenderId());
        vo.setSenderNickname(sender != null ? sender.getAnonymousName() : "匿名旅人");
        User receiver = userMapper.selectById(letter.getReceiverId());
        vo.setReceiverNickname(receiver != null ? receiver.getAnonymousName() : "匿名旅人");

        // 如果是漂流瓶回复来源，填充来源信息
        if ("bottle_reply".equals(letter.getSourceType()) && letter.getSourceId() != null) {
            DriftBottleReply reply = driftBottleReplyMapper.selectById(letter.getSourceId());
            if (reply != null) {
                vo.setSourceReplyContent(reply.getContent());
                DriftBottle bottle = driftBottleMapper.selectById(reply.getBottleId());
                if (bottle != null) {
                    vo.setSourceBottleContent(bottle.getContent());
                }
            }
        }

        return vo;
    }

    @Override
    public void thankLetter(Long userId, Long letterId) {
        userService.checkUserNotMuted(userId);

        Letter letter = letterMapper.selectById(letterId);
        if (letter == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "信件不存在");
        }
        // 校验当前用户是 letter.receiver_id（只有收信人可感谢）
        if (!userId.equals(letter.getReceiverId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅收信人可感谢信件");
        }

        // 校验未感谢过
        List<Long> thankedBy = parseThankedBy(letter.getThankedBy());
        if (thankedBy.contains(userId)) {
            throw new BusinessException(ErrorCode.ALREADY_THANKED);
        }

        // 更新 thanked_by
        thankedBy.add(userId);
        letterMapper.update(null, new LambdaUpdateWrapper<Letter>()
                .eq(Letter::getId, letterId)
                .set(Letter::getThankedBy, serializeThankedBy(thankedBy)));

        // 给发送者 +1代币 +1萤火
        thankReward(letter.getSenderId(), letterId);
        log.info("信件感谢成功: userId={}, letterId={}, targetUserId={}", userId, letterId, letter.getSenderId());
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 感谢奖励：给目标用户 +1代币 +1萤火（事务内）
     */
    private void thankReward(Long targetUserId, Long refId) {
        User user = userMapper.selectById(targetUserId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        user.setTokenBalance(user.getTokenBalance() + 1);
        user.setTotalFirefly(user.getTotalFirefly() + 1);
        user.setFireflyBalance(user.getFireflyBalance() + 1);
        boolean updated = userMapper.updateById(user) > 0;
        if (!updated) {
            throw new BusinessException(ErrorCode.CONFLICT, "感谢奖励处理冲突，请重试");
        }
        TokenTransaction tx = new TokenTransaction();
        tx.setUserId(targetUserId);
        tx.setType("earn");
        tx.setAmount(1);
        tx.setSource("receive_thanks");
        tx.setRefId(refId);
        tokenTransactionMapper.insert(tx);
    }

    @Override
    public void markAsRead(Long userId, Long letterId) {
        Letter letter = letterMapper.selectById(letterId);
        if (letter == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "信件不存在");
        }
        if (!userId.equals(letter.getReceiverId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅收信人可标记已读");
        }
        letterMapper.update(null, new LambdaUpdateWrapper<Letter>()
                .eq(Letter::getId, letterId)
                .set(Letter::getIsRead, 1));
    }

    /**
     * 解析 thanked_by JSON 数组
     */
    private List<Long> parseThankedBy(String thankedBy) {
        if (!StringUtils.hasText(thankedBy)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(thankedBy, new TypeReference<List<Long>>() {});
        } catch (JsonProcessingException e) {
            log.warn("解析 thanked_by 失败: {}", thankedBy, e);
            return new ArrayList<>();
        }
    }

    /**
     * 序列化 thanked_by 为 JSON 数组字符串
     */
    private String serializeThankedBy(List<Long> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "JSON序列化失败");
        }
    }

    private LetterVO toVO(Letter letter) {
        LetterVO vo = new LetterVO();
        vo.setId(letter.getId());
        vo.setSenderId(letter.getSenderId());
        vo.setReceiverId(letter.getReceiverId());
        vo.setParentId(letter.getParentId());
        vo.setSourceType(letter.getSourceType());
        vo.setSourceId(letter.getSourceId());
        vo.setContent(letter.getContent());
        vo.setIsReplied(letter.getIsReplied());
        vo.setIsRead(letter.getIsRead());
        vo.setCreatedAt(letter.getCreatedAt());
        return vo;
    }

    private List<LetterVO> toVOList(List<Letter> letters) {
        if (letters.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> userIds = new HashSet<>();
        letters.forEach(l -> {
            if (l.getSenderId() != null) userIds.add(l.getSenderId());
            if (l.getReceiverId() != null) userIds.add(l.getReceiverId());
        });

        Map<Long, User> userMap = userIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        return letters.stream().map(l -> {
            LetterVO vo = toVO(l);
            User sender = userMap.get(l.getSenderId());
            vo.setSenderNickname(sender != null ? sender.getAnonymousName() : "匿名旅人");
            User receiver = userMap.get(l.getReceiverId());
            vo.setReceiverNickname(receiver != null ? receiver.getAnonymousName() : "匿名旅人");
            return vo;
        }).collect(Collectors.toList());
    }
}
