package com.glimmer.service.impl;

import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.entity.SignInRecord;
import com.glimmer.entity.TokenTransaction;
import com.glimmer.entity.User;
import com.glimmer.mapper.SignInRecordMapper;
import com.glimmer.mapper.TokenTransactionMapper;
import com.glimmer.mapper.UserMapper;
import com.glimmer.service.dto.SignInResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 代币服务单元测试（Mockito，不依赖数据库）
 * 覆盖：签到成功（前7天 +3）、重复签到失败、第8天签到（+1）
 * 见开发文档 §2.2
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("代币服务测试 - TokenServiceImpl")
class TokenServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private SignInRecordMapper signInRecordMapper;
    @Mock
    private TokenTransactionMapper tokenTransactionMapper;

    @InjectMocks
    private TokenServiceImpl tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenServiceImpl(userMapper, signInRecordMapper, tokenTransactionMapper);
    }

    @Test
    @DisplayName("首次签到成功：累计0→1天，奖励 +3 代币")
    void signIn_firstTime_shouldReward3Tokens() {
        // given
        Long userId = 10L;
        User user = new User();
        user.setId(userId);
        user.setStatus("active");
        user.setTokenBalance(0);
        user.setTotalSignDays(0);
        user.setVersion(0);

        when(userMapper.selectById(userId)).thenReturn(user);
        when(signInRecordMapper.selectCount(any())).thenReturn(0L);
        // 模拟 insert 回填签到记录 id
        doAnswer(invocation -> {
            SignInRecord r = invocation.getArgument(0);
            r.setId(500L);
            return 1;
        }).when(signInRecordMapper).insert(any(SignInRecord.class));
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        // when
        SignInResponse response = tokenService.signIn(userId);

        // then
        assertNotNull(response);
        assertTrue(response.getSignedIn());
        assertEquals(3, response.getReward(), "前7天签到应奖励3代币");
        assertEquals(1, response.getTotalSignDays());

        // 校验用户余额与累计天数更新
        assertEquals(3, user.getTokenBalance());
        assertEquals(1, user.getTotalSignDays());

        // 校验代币流水写入
        ArgumentCaptor<TokenTransaction> txCaptor = ArgumentCaptor.forClass(TokenTransaction.class);
        verify(tokenTransactionMapper).insert(txCaptor.capture());
        TokenTransaction tx = txCaptor.getValue();
        assertEquals(userId, tx.getUserId());
        assertEquals("earn", tx.getType());
        assertEquals(3, tx.getAmount());
        assertEquals("sign_in", tx.getSource());
        assertEquals(500L, tx.getRefId(), "refId 应为签到记录ID");
    }

    @Test
    @DisplayName("重复签到失败：今日已签到时抛出 BusinessException(ALREADY_SIGNED_IN)")
    void signIn_duplicate_throwsAlreadySignedIn() {
        // given
        Long userId = 11L;
        User user = new User();
        user.setId(userId);
        user.setStatus("active");
        user.setTotalSignDays(1);

        when(userMapper.selectById(userId)).thenReturn(user);
        // 今日已存在签到记录
        when(signInRecordMapper.selectCount(any())).thenReturn(1L);

        // when & then
        BusinessException ex = assertThrows(BusinessException.class, () -> tokenService.signIn(userId));
        assertEquals(ErrorCode.ALREADY_SIGNED_IN.getCode(), ex.getCode());
        // 重复签到不应写入签到记录、不应更新余额、不应写流水
        verify(signInRecordMapper, never()).insert(any(SignInRecord.class));
        verify(userMapper, never()).updateById(any(User.class));
        verify(tokenTransactionMapper, never()).insert(any(TokenTransaction.class));
    }

    @Test
    @DisplayName("第8天签到：累计7→8天，奖励 +1 代币")
    void signIn_day8_shouldReward1Token() {
        // given
        Long userId = 12L;
        User user = new User();
        user.setId(userId);
        user.setStatus("active");
        user.setTokenBalance(21);
        user.setTotalSignDays(7);
        user.setVersion(0);

        when(userMapper.selectById(userId)).thenReturn(user);
        when(signInRecordMapper.selectCount(any())).thenReturn(0L);
        when(signInRecordMapper.insert(any(SignInRecord.class))).thenReturn(1);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        // when
        SignInResponse response = tokenService.signIn(userId);

        // then
        assertEquals(1, response.getReward(), "第8天起签到应奖励1代币");
        assertEquals(8, response.getTotalSignDays());
        assertEquals(22, user.getTokenBalance(), "余额 21 + 1 = 22");
    }

    @Test
    @DisplayName("签到失败：用户被封禁时抛出 BusinessException(USER_BANNED)")
    void signIn_whenBanned_throwsUserBanned() {
        // given
        Long userId = 13L;
        User user = new User();
        user.setId(userId);
        user.setStatus("banned");

        when(userMapper.selectById(userId)).thenReturn(user);

        // when & then
        BusinessException ex = assertThrows(BusinessException.class, () -> tokenService.signIn(userId));
        assertEquals(ErrorCode.USER_BANNED.getCode(), ex.getCode());
        verify(signInRecordMapper, never()).insert(any(SignInRecord.class));
    }
}
