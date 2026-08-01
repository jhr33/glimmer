package com.glimmer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.response.PageResult;
import com.glimmer.common.util.RedisUtils;
import com.glimmer.common.util.TokenBalanceHelper;
import com.glimmer.entity.SignInRecord;
import com.glimmer.entity.TokenTransaction;
import com.glimmer.entity.User;
import com.glimmer.mapper.SignInRecordMapper;
import com.glimmer.mapper.TokenTransactionMapper;
import com.glimmer.mapper.UserMapper;
import com.glimmer.service.PunishmentService;
import com.glimmer.service.TokenService;
import com.glimmer.service.dto.SignInResponse;
import com.glimmer.service.dto.SignInStatusResponse;
import com.glimmer.service.dto.TransactionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 代币服务实现（签到、流水查询）
 * 见开发文档 §2.2
 */
@Slf4j
@Service
public class TokenServiceImpl implements TokenService {

    /** 累计签到 1-7 天每天获得代币数 */
    private static final int EARLY_REWARD = 3;
    /** 第 8 天起每天获得代币数 */
    private static final int LATE_REWARD = 1;
    /** 前 7 天阈值 */
    private static final int EARLY_DAYS_THRESHOLD = 7;

    /** 缓存 key：签到状态 signin:{userId}:{yyyyMMdd}，TTL 到次日 0 点 */
    private static final String CACHE_KEY_SIGNIN = "signin:%d:%s";

    private final UserMapper userMapper;
    private final SignInRecordMapper signInRecordMapper;
    private final TokenTransactionMapper tokenTransactionMapper;
    private final PunishmentService punishmentService;
    private final RedisUtils redis;
    private final TokenBalanceHelper tokenBalanceHelper;

    public TokenServiceImpl(UserMapper userMapper, SignInRecordMapper signInRecordMapper,
                            TokenTransactionMapper tokenTransactionMapper,
                            PunishmentService punishmentService, RedisUtils redis,
                            TokenBalanceHelper tokenBalanceHelper) {
        this.userMapper = userMapper;
        this.signInRecordMapper = signInRecordMapper;
        this.tokenTransactionMapper = tokenTransactionMapper;
        this.punishmentService = punishmentService;
        this.redis = redis;
        this.tokenBalanceHelper = tokenBalanceHelper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SignInResponse signIn(Long userId) {
        // 1. 查询用户并校验状态
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if ("banned".equals(user.getStatus())) {
            boolean hasActivePunishment = punishmentService.isUserBanned(userId);
            if (hasActivePunishment) {
                throw new BusinessException(ErrorCode.USER_BANNED);
            }
            // 处罚已全部结束，自动恢复为active
            user.setStatus("active");
            userMapper.updateById(user);
            log.info("用户签到时发现处罚已结束，自动恢复为active: userId={}", userId);
        }

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));

        // 2. 预检查今日是否已签到（优先读 Redis 缓存，避免查 DB）
        String signinKey = String.format(CACHE_KEY_SIGNIN, userId, today.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
        if ("1".equals(redis.get(signinKey))) {
            throw new BusinessException(ErrorCode.ALREADY_SIGNED_IN);
        }
        // 缓存未命中，查 DB 兜底
        Long existCount = signInRecordMapper.selectCount(new LambdaQueryWrapper<SignInRecord>()
                .eq(SignInRecord::getUserId, userId)
                .eq(SignInRecord::getSignDate, today));
        if (existCount != null && existCount > 0) {
            // DB 已签到但缓存未命中，回填缓存
            redis.set(signinKey, "1", RedisUtils.ttlUntilEndOfDay());
            throw new BusinessException(ErrorCode.ALREADY_SIGNED_IN);
        }

        // 3. 计算奖励：累计签到1-7天 +3，第8天起 +1
        int newTotalDays = user.getTotalSignDays() + 1;
        int reward = newTotalDays <= EARLY_DAYS_THRESHOLD ? EARLY_REWARD : LATE_REWARD;

        // 4. 写入签到记录（uk_user_date 唯一约束兜底并发安全）
        SignInRecord record = new SignInRecord();
        record.setUserId(userId);
        record.setSignDate(today);
        signInRecordMapper.insert(record);

        // 5. 更新用户代币余额与累计签到天数（分布式锁 + 乐观锁兜底）
        tokenBalanceHelper.modifyWithLock(userId, u -> {
            u.setTokenBalance(u.getTokenBalance() + reward);
            u.setTotalSignDays(newTotalDays);
        });

        // 6. 写入代币流水（type=earn, source=sign_in, ref_id=签到记录ID）
        TokenTransaction tx = new TokenTransaction();
        tx.setUserId(userId);
        tx.setType("earn");
        tx.setAmount(reward);
        tx.setSource("sign_in");
        tx.setRefId(record.getId());
        tokenTransactionMapper.insert(tx);

        log.info("用户签到成功: userId={}, reward={}, totalSignDays={}", userId, reward, newTotalDays);

        // 写入签到缓存，TTL 到次日 0 点
        redis.set(signinKey, "1", RedisUtils.ttlUntilEndOfDay());

        SignInResponse response = new SignInResponse();
        response.setSignedIn(true);
        response.setReward(reward);
        response.setTotalSignDays(newTotalDays);
        return response;
    }

    @Override
    public SignInStatusResponse getSignInStatus(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));

        // 优先读 Redis 缓存
        String signinKey = String.format(CACHE_KEY_SIGNIN, userId, today.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
        boolean signedInToday;
        if ("1".equals(redis.get(signinKey))) {
            signedInToday = true;
        } else {
            // 缓存未命中，查 DB 并回填
            Long count = signInRecordMapper.selectCount(new LambdaQueryWrapper<SignInRecord>()
                    .eq(SignInRecord::getUserId, userId)
                    .eq(SignInRecord::getSignDate, today));
            signedInToday = count != null && count > 0;
            if (signedInToday) {
                redis.set(signinKey, "1", RedisUtils.ttlUntilEndOfDay());
            }
        }

        SignInStatusResponse response = new SignInStatusResponse();
        response.setSignedInToday(signedInToday);
        response.setTotalSignDays(user.getTotalSignDays());
        return response;
    }

    @Override
    public PageResult<TransactionVO> getTransactions(Long userId, String type, String source, int page, int size) {
        Page<TokenTransaction> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<TokenTransaction> wrapper = new LambdaQueryWrapper<TokenTransaction>()
                .eq(TokenTransaction::getUserId, userId)
                .eq(StringUtils.hasText(type), TokenTransaction::getType, type)
                .eq(StringUtils.hasText(source), TokenTransaction::getSource, source)
                .orderByDesc(TokenTransaction::getCreatedAt);

        IPage<TokenTransaction> result = tokenTransactionMapper.selectPage(pageParam, wrapper);
        List<TransactionVO> list = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(list, result.getTotal(), page, size);
    }

    private TransactionVO toVO(TokenTransaction tx) {
        TransactionVO vo = new TransactionVO();
        vo.setId(tx.getId());
        vo.setType(tx.getType());
        vo.setAmount(tx.getAmount());
        vo.setSource(tx.getSource());
        vo.setRefId(tx.getRefId());
        vo.setCreatedAt(tx.getCreatedAt());
        return vo;
    }
}
