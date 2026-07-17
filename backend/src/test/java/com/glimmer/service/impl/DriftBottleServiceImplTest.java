package com.glimmer.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.entity.DriftBottle;
import com.glimmer.entity.DriftBottlePickRecord;
import com.glimmer.entity.DriftBottleReply;
import com.glimmer.entity.User;
import com.glimmer.mapper.DriftBottleMapper;
import com.glimmer.mapper.DriftBottlePickRecordMapper;
import com.glimmer.mapper.DriftBottleReplyMapper;
import com.glimmer.mapper.TokenTransactionMapper;
import com.glimmer.mapper.UserMapper;
import com.glimmer.service.dto.BottlePickVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 漂流瓶服务单元测试（Mockito，不依赖数据库）
 * 覆盖：扔瓶子成功、捡瓶子不会捡到自己的、重复捡同一瓶子被拒绝、回复瓶子成功（每人一次）
 * 见开发文档 §2.3
 *
 * 说明：DriftBottleServiceImpl.pickBottle 内部使用 LambdaQueryWrapper.select() 会触发
 * MyBatis-Plus 的 lambda 缓存解析，需在 @BeforeAll 中手动初始化 TableInfo 缓存。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("漂流瓶服务测试 - DriftBottleServiceImpl")
class DriftBottleServiceImplTest {

    @Mock
    private DriftBottleMapper driftBottleMapper;
    @Mock
    private DriftBottleReplyMapper driftBottleReplyMapper;
    @Mock
    private DriftBottlePickRecordMapper driftBottlePickRecordMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private TokenTransactionMapper tokenTransactionMapper;

    private ObjectMapper objectMapper;
    private DriftBottleServiceImpl driftBottleService;

    /**
     * 初始化 MyBatis-Plus 实体元数据缓存，使 LambdaQueryWrapper 能解析 lambda 字段。
     * 纯 Mockito 单元测试不经过 MyBatis-Plus 自动配置，需手动初始化。
     */
    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, DriftBottle.class);
        TableInfoHelper.initTableInfo(assistant, DriftBottlePickRecord.class);
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        driftBottleService = new DriftBottleServiceImpl(
                driftBottleMapper, driftBottleReplyMapper, driftBottlePickRecordMapper,
                userMapper, tokenTransactionMapper, objectMapper);
    }

    @Test
    @DisplayName("扔瓶子成功：状态为 drifting，记录投放者")
    void throwBottle_success_shouldInsertDriftingBottle() {
        // given
        Long userId = 20L;
        User user = new User();
        user.setId(userId);
        user.setStatus("active");
        when(userMapper.selectById(userId)).thenReturn(user);

        // when
        driftBottleService.throwBottle(userId, "今天天气不错");

        // then
        ArgumentCaptor<DriftBottle> captor = ArgumentCaptor.forClass(DriftBottle.class);
        verify(driftBottleMapper).insert(captor.capture());
        DriftBottle bottle = captor.getValue();
        assertEquals(userId, bottle.getUserId());
        assertEquals("今天天气不错", bottle.getContent());
        assertEquals("drifting", bottle.getStatus(), "新扔出的瓶子状态应为 drifting");
    }

    @Test
    @DisplayName("扔瓶子失败：用户被封禁时抛出 BusinessException(USER_BANNED)")
    void throwBottle_whenBanned_throwsUserBanned() {
        // given
        Long userId = 21L;
        User user = new User();
        user.setId(userId);
        user.setStatus("banned");
        when(userMapper.selectById(userId)).thenReturn(user);

        // when & then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> driftBottleService.throwBottle(userId, "test"));
        assertEquals(ErrorCode.USER_BANNED.getCode(), ex.getCode());
        verify(driftBottleMapper, never()).insert(any(DriftBottle.class));
    }

    @Test
    @DisplayName("捡瓶子成功：不会捡到自己的瓶子（查询排除自己的 userId）")
    void pickBottle_success_shouldNotPickOwnBottle() {
        // given
        Long userId = 30L;
        User user = new User();
        user.setId(userId);
        user.setStatus("active");
        when(userMapper.selectById(userId)).thenReturn(user);
        // 无已捡记录
        when(driftBottlePickRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        // 模拟查询到的瓶子是别人的（userId=99，不是当前用户 30）
        DriftBottle otherBottle = new DriftBottle();
        otherBottle.setId(888L);
        otherBottle.setUserId(99L);
        otherBottle.setContent("来自他人的瓶子");
        otherBottle.setStatus("drifting");
        otherBottle.setCreatedAt(LocalDateTime.now());
        when(driftBottleMapper.selectList(any())).thenReturn(List.of(otherBottle));

        // when
        BottlePickVO vo = driftBottleService.pickBottle(userId);

        // then
        assertNotNull(vo);
        assertEquals(888L, vo.getBottleId());
        assertNotEquals(userId, otherBottle.getUserId(), "捡到的瓶子不应是自己的");
        // 校验捡瓶记录写入
        ArgumentCaptor<DriftBottlePickRecord> recordCaptor = ArgumentCaptor.forClass(DriftBottlePickRecord.class);
        verify(driftBottlePickRecordMapper).insert(recordCaptor.capture());
        DriftBottlePickRecord record = recordCaptor.getValue();
        assertEquals(888L, record.getBottleId());
        assertEquals(30L, record.getUserId());
        assertEquals(0, record.getOpened(), "新捡记录 opened 应为 0");
    }

    @Test
    @DisplayName("捡瓶子：无可用瓶子时返回 null")
    void pickBottle_whenNoBottleAvailable_returnsNull() {
        // given
        Long userId = 31L;
        User user = new User();
        user.setId(userId);
        user.setStatus("active");
        when(userMapper.selectById(userId)).thenReturn(user);
        when(driftBottlePickRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(driftBottleMapper.selectList(any())).thenReturn(Collections.emptyList());

        // when
        BottlePickVO vo = driftBottleService.pickBottle(userId);

        // then
        assertNull(vo, "无可用瓶子时应返回 null");
        verify(driftBottlePickRecordMapper, never()).insert(any(DriftBottlePickRecord.class));
    }

    @Test
    @DisplayName("重复捡同一瓶子被拒绝：uk_bottle_user 冲突时抛出 BusinessException(CONFLICT)")
    void pickBottle_duplicate_throwsConflict() {
        // given
        Long userId = 32L;
        User user = new User();
        user.setId(userId);
        user.setStatus("active");
        when(userMapper.selectById(userId)).thenReturn(user);
        when(driftBottlePickRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        DriftBottle bottle = new DriftBottle();
        bottle.setId(777L);
        bottle.setUserId(100L);
        bottle.setStatus("drifting");
        when(driftBottleMapper.selectList(any())).thenReturn(List.of(bottle));
        // 模拟 uk_bottle_user 唯一约束冲突（并发场景）
        when(driftBottlePickRecordMapper.insert(any(DriftBottlePickRecord.class)))
                .thenThrow(new DuplicateKeyException("uk_bottle_user"));

        // when & then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> driftBottleService.pickBottle(userId));
        assertEquals(ErrorCode.CONFLICT.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("回复瓶子成功：写入回复记录")
    void replyBottle_success_shouldInsertReply() {
        // given
        Long userId = 40L;
        Long bottleId = 50L;
        User user = new User();
        user.setId(userId);
        user.setStatus("active");
        when(userMapper.selectById(userId)).thenReturn(user);
        // 已捡到该瓶子
        when(driftBottlePickRecordMapper.selectCount(any())).thenReturn(1L);

        // when
        driftBottleService.replyBottle(userId, bottleId, "加油呀");

        // then
        ArgumentCaptor<DriftBottleReply> captor = ArgumentCaptor.forClass(DriftBottleReply.class);
        verify(driftBottleReplyMapper).insert(captor.capture());
        DriftBottleReply reply = captor.getValue();
        assertEquals(bottleId, reply.getBottleId());
        assertEquals(userId, reply.getUserId());
        assertEquals("加油呀", reply.getContent());
    }

    @Test
    @DisplayName("回复瓶子失败：每人只能回复一次，重复回复抛出 BusinessException(ALREADY_REPLIED_BOTTLE)")
    void replyBottle_duplicate_throwsAlreadyReplied() {
        // given
        Long userId = 41L;
        Long bottleId = 51L;
        User user = new User();
        user.setId(userId);
        user.setStatus("active");
        when(userMapper.selectById(userId)).thenReturn(user);
        when(driftBottlePickRecordMapper.selectCount(any())).thenReturn(1L);
        // 模拟 uk_bottle_user 唯一约束冲突
        when(driftBottleReplyMapper.insert(any(DriftBottleReply.class)))
                .thenThrow(new DuplicateKeyException("uk_bottle_user"));

        // when & then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> driftBottleService.replyBottle(userId, bottleId, "再次回复"));
        assertEquals(ErrorCode.ALREADY_REPLIED_BOTTLE.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("回复瓶子失败：未捡到该瓶子时抛出 BusinessException(FORBIDDEN)")
    void replyBottle_notPicked_throwsForbidden() {
        // given
        Long userId = 42L;
        Long bottleId = 52L;
        User user = new User();
        user.setId(userId);
        user.setStatus("active");
        when(userMapper.selectById(userId)).thenReturn(user);
        // 未捡到该瓶子
        when(driftBottlePickRecordMapper.selectCount(any())).thenReturn(0L);

        // when & then
        BusinessException ex = assertThrows(BusinessException.class,
                () -> driftBottleService.replyBottle(userId, bottleId, "回复"));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
        verify(driftBottleReplyMapper, never()).insert(any(DriftBottleReply.class));
    }
}
