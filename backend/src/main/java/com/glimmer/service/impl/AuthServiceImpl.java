package com.glimmer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.util.AnonymousNameGenerator;
import com.glimmer.common.util.JwtUtils;
import com.glimmer.entity.User;
import com.glimmer.mapper.UserMapper;
import com.glimmer.service.AuthService;
import com.glimmer.service.dto.LoginRequest;
import com.glimmer.service.dto.LoginResponse;
import com.glimmer.service.dto.RegisterRequest;
import com.glimmer.service.dto.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 鉴权服务实现（注册、登录）
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public AuthServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtils jwtUtils) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long register(RegisterRequest request) {
        // 1. 构建用户实体，初始化默认字段（见开发文档 §2.1.1）
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole("user");
        user.setStatus("active");
        user.setTokenBalance(0);
        user.setTotalFirefly(0);
        user.setFireflyBalance(0);
        user.setTotalSignDays(0);
        user.setPendingReportCount(0);
        user.setVersion(0);

        // 2. 插入用户（uk_username 唯一约束冲突由全局异常处理器捕获并返回 4002）
        userMapper.insert(user);

        // 3. 根据生成的用户ID生成匿名昵称（见开发文档 §2.1.5）
        String anonymousName = AnonymousNameGenerator.generate(user.getId());
        user.setAnonymousName(anonymousName);
        userMapper.updateById(user);

        log.info("用户注册成功: id={}, username={}, anonymousName={}", user.getId(), user.getUsername(), anonymousName);
        return user.getId();
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        // 1. 按用户名查询
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        if (user == null) {
            throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD_ERROR);
        }

        // 2. 校验密码（BCrypt）
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD_ERROR);
        }

        // 3. 校验用户状态（见开发文档 §2.1.2）
        if ("banned".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_BANNED);
        }

        // 4. 签发 JWT（24小时，HS256）
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());

        // 5. 构建响应（不含敏感字段）
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUser(toUserVO(user));
        log.info("用户登录成功: id={}, username={}", user.getId(), user.getUsername());
        return response;
    }

    private UserVO toUserVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAnonymousName(user.getAnonymousName());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setMuteType(user.getMuteType());
        vo.setMuteEndTime(user.getMuteEndTime());
        vo.setTokenBalance(user.getTokenBalance());
        vo.setTotalFirefly(user.getTotalFirefly());
        vo.setFireflyBalance(user.getFireflyBalance());
        vo.setTotalSignDays(user.getTotalSignDays());
        return vo;
    }
}
