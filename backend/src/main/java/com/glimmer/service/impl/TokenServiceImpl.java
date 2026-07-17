package com.glimmer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.response.PageResult;
import com.glimmer.entity.SignInRecord;
import com.glimmer.entity.TokenTransaction;
import com.glimmer.entity.User;
import com.glimmer.mapper.SignInRecordMapper;
import com.glimmer.mapper.TokenTransactionMapper;
import com.glimmer.mapper.UserMapper;
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

    private final UserMapper userMapper;
    private final SignInRecordMapper signInRecordMapper;
    private final TokenTransactionMapper tokenTransactionMapper;

    public TokenServiceImpl(UserMapper userMapper, SignInRecordMapper signInRecordMapper,
                            TokenTransactionMapper tokenTransactionMapper) {
        this.userMapper = userMapper;
        this.signInRecordMapper = signInRecordMapper;
        this.tokenTransactionMapper = tokenTransactionMapper;
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
            throw new BusinessException(ErrorCode.USER_BANNED);
        }

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));

        // 2. 预检查今日是否已签到（友好错误提示）
        Long existCount = signInRecordMapper.selectCount(new LambdaQueryWrapper<SignInRecord>()
                .eq(SignInRecord::getUserId, userId)
                .eq(SignInRecord::getSignDate, today));
        if (existCount != null && existCount > 0) {
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

        // 5. 更新用户代币余额与累计签到天数（乐观锁 @Version）
        user.setTokenBalance(user.getTokenBalance() + reward);
        user.setTotalSignDays(newTotalDays);
        boolean updated = userMapper.updateById(user) > 0;
        if (!updated) {
            // 乐观锁冲突（极少数并发场景），uk_user_date 已保证不会重复签到
            throw new BusinessException(ErrorCode.CONFLICT, "签到处理冲突，请重试");
        }

        // 6. 写入代币流水（type=earn, source=sign_in, ref_id=签到记录ID）
        TokenTransaction tx = new TokenTransaction();
        tx.setUserId(userId);
        tx.setType("earn");
        tx.setAmount(reward);
        tx.setSource("sign_in");
        tx.setRefId(record.getId());
        tokenTransactionMapper.insert(tx);

        log.info("用户签到成功: userId={}, reward={}, totalSignDays={}", userId, reward, newTotalDays);

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
        Long count = signInRecordMapper.selectCount(new LambdaQueryWrapper<SignInRecord>()
                .eq(SignInRecord::getUserId, userId)
                .eq(SignInRecord::getSignDate, today));

        SignInStatusResponse response = new SignInStatusResponse();
        response.setSignedInToday(count != null && count > 0);
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
