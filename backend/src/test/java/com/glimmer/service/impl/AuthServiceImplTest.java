package com.glimmer.service.impl;

import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.util.AnonymousNameGenerator;
import com.glimmer.common.util.JwtUtils;
import com.glimmer.entity.User;
import com.glimmer.mapper.UserMapper;
import com.glimmer.service.dto.LoginRequest;
import com.glimmer.service.dto.LoginResponse;
import com.glimmer.service.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 鉴权服务单元测试（Mockito，不依赖数据库）
 * 覆盖：注册成功（生成匿名昵称）、注册失败（用户名已存在）、登录成功、登录失败（密码错误）
 * 见开发文档 §2.1
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("鉴权服务测试 - AuthServiceImpl")
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userMapper, passwordEncoder, jwtUtils);
    }

    @Test
    @DisplayName("注册成功：密码 BCrypt 加密、生成匿名昵称、初始化默认字段")
    void register_success_shouldEncodePasswordAndGenerateAnonymousName() {
        // given
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("secret123");

        // 模拟 insert 会回填主键 id
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$10$encodedHash");
        doAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(100L);
            return 1;
        }).when(userMapper).insert(any(User.class));

        // when
        Long userId = authService.register(request);

        // then
        assertEquals(100L, userId);

        // 校验 insert 时用户字段已正确初始化
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User inserted = captor.getValue();
        assertEquals("alice", inserted.getUsername());
        assertEquals("$2a$10$encodedHash", inserted.getPassword());
        assertEquals("user", inserted.getRole());
        assertEquals("active", inserted.getStatus());
        assertEquals(0, inserted.getTokenBalance());
        assertEquals(0, inserted.getTotalFirefly());
        assertEquals(0, inserted.getTotalSignDays());
        assertEquals(0, inserted.getPendingReportCount());
        assertEquals(0, inserted.getVersion());

        // 校验生成匿名昵称并回写
        verify(userMapper).updateById(any(User.class));
        ArgumentCaptor<User> updateCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(updateCaptor.capture());
        User updated = updateCaptor.getValue();
        assertNotNull(updated.getAnonymousName());
        // 匿名昵称格式：形容词+的+名词+4位编号
        assertTrue(updated.getAnonymousName().matches(".+的.+\\d{4}"),
                "匿名昵称格式应为 形容词+的+名词+4位编号，实际: " + updated.getAnonymousName());
    }

    @Test
    @DisplayName("注册失败：用户名已存在时抛出 DuplicateKeyException（由全局异常处理器映射为 USERNAME_EXISTS）")
    void register_fail_whenUsernameExists_throwsDuplicateKeyException() {
        // given
        RegisterRequest request = new RegisterRequest();
        request.setUsername("admin");
        request.setPassword("anyPassword");

        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hash");
        // 模拟 uk_username 唯一约束冲突
        when(userMapper.insert(any(User.class))).thenThrow(new DuplicateKeyException("uk_username"));

        // when & then
        assertThrows(DuplicateKeyException.class, () -> authService.register(request));
        // 用户名已存在时不应该回写匿名昵称
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("登录成功：校验密码通过后签发 JWT")
    void login_success_shouldReturnToken() {
        // given
        LoginRequest request = new LoginRequest();
        request.setUsername("bob");
        request.setPassword("pwd123");

        User user = new User();
        user.setId(2L);
        user.setUsername("bob");
        user.setPassword("$2a$10$storedHash");
        user.setRole("user");
        user.setStatus("active");
        user.setNickname("鲍勃");
        user.setAnonymousName("温柔的旅人0002");
        user.setTokenBalance(6);
        user.setTotalFirefly(3);
        user.setFireflyBalance(1);
        user.setTotalSignDays(2);

        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("pwd123", "$2a$10$storedHash")).thenReturn(true);
        when(jwtUtils.generateToken(2L, "bob", "user")).thenReturn("mock-jwt-token");

        // when
        LoginResponse response = authService.login(request);

        // then
        assertNotNull(response);
        assertEquals("mock-jwt-token", response.getToken());
        assertNotNull(response.getUser());
        assertEquals(2L, response.getUser().getId());
        assertEquals("bob", response.getUser().getUsername());
        assertEquals("user", response.getUser().getRole());
        assertEquals(6, response.getUser().getTokenBalance());
        // UserVO 本身不含密码字段（类型设计上已排除敏感信息）
    }

    @Test
    @DisplayName("登录失败：密码错误时抛出 BusinessException(USERNAME_OR_PASSWORD_ERROR)")
    void login_fail_whenPasswordWrong_throwsBusinessException() {
        // given
        LoginRequest request = new LoginRequest();
        request.setUsername("bob");
        request.setPassword("wrongPwd");

        User user = new User();
        user.setId(2L);
        user.setUsername("bob");
        user.setPassword("$2a$10$storedHash");
        user.setStatus("active");

        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("wrongPwd", "$2a$10$storedHash")).thenReturn(false);

        // when & then
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request));
        assertEquals(ErrorCode.USERNAME_OR_PASSWORD_ERROR.getCode(), ex.getCode());
        // 密码错误时不应签发 JWT
        verify(jwtUtils, never()).generateToken(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("登录失败：用户不存在时抛出 BusinessException(USERNAME_OR_PASSWORD_ERROR)")
    void login_fail_whenUserNotFound_throwsBusinessException() {
        // given
        LoginRequest request = new LoginRequest();
        request.setUsername("nobody");
        request.setPassword("anyPwd");

        when(userMapper.selectOne(any())).thenReturn(null);

        // when & then
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request));
        assertEquals(ErrorCode.USERNAME_OR_PASSWORD_ERROR.getCode(), ex.getCode());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtUtils, never()).generateToken(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("登录失败：用户被封禁时抛出 BusinessException(USER_BANNED)")
    void login_fail_whenUserBanned_throwsBusinessException() {
        // given
        LoginRequest request = new LoginRequest();
        request.setUsername("bannedUser");
        request.setPassword("pwd");

        User user = new User();
        user.setId(3L);
        user.setUsername("bannedUser");
        user.setPassword("$2a$10$hash");
        user.setStatus("banned");

        when(userMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        // when & then
        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(request));
        assertEquals(ErrorCode.USER_BANNED.getCode(), ex.getCode());
        verify(jwtUtils, never()).generateToken(any(), anyString(), anyString());
    }
}
