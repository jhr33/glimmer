package com.glimmer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.util.AnonymousNameGenerator;
import com.glimmer.common.util.JwtUtils;
import com.glimmer.config.security.LoginSessionManager;
import com.glimmer.entity.Punishment;
import com.glimmer.entity.User;
import com.glimmer.mapper.UserMapper;
import com.glimmer.service.AuthService;
import com.glimmer.service.PunishmentService;
import com.glimmer.service.SmsService;
import com.glimmer.service.dto.BindPhoneRequest;
import com.glimmer.service.dto.LoginRequest;
import com.glimmer.service.dto.LoginResponse;
import com.glimmer.service.dto.PhoneLoginRequest;
import com.glimmer.service.dto.RegisterRequest;
import com.glimmer.service.dto.SmsLoginRequest;
import com.glimmer.service.dto.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 鉴权服务实现（注册、登录）
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    /** UID 起始基数：对外 UID = 10000 + user.id，避免暴露自增主键和真实用户量 */
    private static final long UID_BASE = 10000L;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final PunishmentService punishmentService;
    private final LoginSessionManager loginSessionManager;
    private final SmsService smsService;

    public AuthServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtils jwtUtils,
                           PunishmentService punishmentService, LoginSessionManager loginSessionManager,
                           SmsService smsService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.punishmentService = punishmentService;
        this.loginSessionManager = loginSessionManager;
        this.smsService = smsService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long register(RegisterRequest request) {
        String phone = request.getPhone();
        String code = request.getCode();
        String password = request.getPassword();

        // 密码必填
        if (password == null || password.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "密码不能为空");
        }
        // 所有注册均需手机号 + 验证码
        if (phone == null || phone.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "手机号不能为空");
        }
        if (code == null || code.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "验证码不能为空");
        }

        // 1. 校验短信验证码（scene=register）
        smsService.verifyCode(phone, SmsService.SCENE_REGISTER, code);
        // 2. 校验手机号未被占用（复用 4026 PHONE_ALREADY_BOUND，前端据此弹"是否去登录"确认框）
        Long existCount = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, phone));
        if (existCount != null && existCount > 0) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_BOUND);
        }
        // 3. 用户名自动生成 phone_后6位
        String username = generatePhoneUsername(phone);

        // 4. 构建用户实体，初始化默认字段（见开发文档 §2.1.1）
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setPhone(phone);
        user.setRole("user");
        user.setStatus("active");
        user.setTokenBalance(0);
        user.setTotalFirefly(0);
        user.setFireflyBalance(0);
        user.setTotalSignDays(0);
        user.setPendingReportCount(0);
        user.setVersion(0);

        // 5. 插入用户（uk_username 唯一约束冲突由全局异常处理器捕获并返回 4002）
        userMapper.insert(user);

        // 6. 根据生成的用户ID生成初始匿名昵称，有效期 24 小时（过期后下次使用匿名身份时自动轮换）
        String anonymousName = AnonymousNameGenerator.generate(user.getId());
        user.setAnonymousName(anonymousName);
        user.setAnonymousNameExpiresAt(LocalDateTime.now().plusHours(24));
        userMapper.updateById(user);

        log.info("用户注册成功: id={}, username={}, phone={}, anonymousName={}",
                user.getId(), user.getUsername(), phone, anonymousName);
        return user.getId();
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        String account = request.getAccount();
        // 1. 智能识别账号类型：
        //    - 11 位且 1[3-9] 开头 → 手机号查询
        //    - 纯数字 → UID 反推 id = uid - 10000 后按主键查询
        //    - 其他 → 拒绝（用户名登录已下线）
        User user;
        if (account.matches("^1[3-9]\\d{9}$")) {
            user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getPhone, account));
        } else if (account.matches("^\\d+$")) {
            long uid = Long.parseLong(account);
            long id = uid - UID_BASE;
            if (id < 1) {
                throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD_ERROR);
            }
            user = userMapper.selectById(id);
        } else {
            // 不再支持用户名登录，直接拒绝
            throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD_ERROR);
        }
        if (user == null) {
            throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD_ERROR);
        }

        // 2. 校验密码（BCrypt）
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD_ERROR);
        }

        // 3. 校验用户状态（仅管理员手动封禁才拒绝登录）
        checkUserStatusForLogin(user);

        // 4. 签发会话与 JWT
        return buildLoginResponse(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse loginBySms(SmsLoginRequest request) {
        // 1. 校验短信验证码
        smsService.verifyCode(request.getPhone(), SmsService.SCENE_LOGIN, request.getCode());

        // 2. 按手机号查询用户
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, request.getPhone()));

        // 3. 未注册则自动建号（用户名=phone_手机号后6位）
        if (user == null) {
            user = new User();
            user.setPhone(request.getPhone());
            user.setUsername(generatePhoneUsername(request.getPhone()));
            // 初始密码随机生成（用户后续可修改）
            user.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
            user.setRole("user");
            user.setStatus("active");
            user.setTokenBalance(0);
            user.setTotalFirefly(0);
            user.setFireflyBalance(0);
            user.setTotalSignDays(0);
            user.setPendingReportCount(0);
            user.setVersion(0);
            userMapper.insert(user);

            // 生成匿名昵称
            String anonymousName = AnonymousNameGenerator.generate(user.getId());
            user.setAnonymousName(anonymousName);
            user.setAnonymousNameExpiresAt(LocalDateTime.now().plusHours(24));
            userMapper.updateById(user);
            log.info("手机号验证码登录自动注册: userId={}, phone={}", user.getId(), request.getPhone());
        }

        // 4. 校验用户状态（仅管理员手动封禁才拒绝登录）
        checkUserStatusForLogin(user);

        // 5. 签发会话与 JWT
        return buildLoginResponse(user);
    }

    @Override
    public LoginResponse loginByPhone(PhoneLoginRequest request) {
        // 1. 按手机号查询
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, request.getPhone()));
        if (user == null) {
            throw new BusinessException(ErrorCode.PHONE_NOT_REGISTERED);
        }

        // 2. 校验密码（BCrypt）
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.USERNAME_OR_PASSWORD_ERROR);
        }

        // 3. 校验用户状态
        checkUserStatusForLogin(user);

        // 4. 签发会话与 JWT
        return buildLoginResponse(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindPhone(Long userId, BindPhoneRequest request) {
        // 1. 校验短信验证码（已证明对新手机号的所有权）
        smsService.verifyCode(request.getPhone(), SmsService.SCENE_BIND, request.getCode());

        // 2. 检查手机号是否已被其他用户绑定
        User existUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, request.getPhone()));
        if (existUser != null && !existUser.getId().equals(userId)) {
            // 已被其他账户占用：未传 force 直接拒绝；force=true 则解绑旧账户再绑到当前账户
            if (!Boolean.TRUE.equals(request.getForce())) {
                throw new BusinessException(ErrorCode.PHONE_ALREADY_BOUND);
            }
            // 解绑旧账户：phone 置 NULL（MyBatis-Plus updateById 默认不更新 null 字段，需用 UpdateWrapper 显式 set）
            userMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>()
                    .eq(User::getId, existUser.getId())
                    .set(User::getPhone, null));
            log.info("手机号强制解绑并转移: phone={}, 旧账户 userId={} → 新账户 userId={}",
                    request.getPhone(), existUser.getId(), userId);
        }

        // 3. 更新当前用户手机号
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        user.setPhone(request.getPhone());
        userMapper.updateById(user);
        log.info("绑定手机号成功: userId={}, phone={}", userId, request.getPhone());
    }

    /**
     * 生成手机号用户名：phone_ + 手机号后6位（唯一索引兜底）
     */
    private String generatePhoneUsername(String phone) {
        return "phone_" + phone.substring(phone.length() - 6);
    }

    /**
     * 登录时校验用户状态：仅管理员手动封禁（user.status='banned' 且 punishment 表有生效 BAN）才拒绝
     */
    private void checkUserStatusForLogin(User user) {
        if ("banned".equals(user.getStatus())) {
            if (punishmentService.isUserBanned(user.getId())) {
                throw new BusinessException(ErrorCode.USER_BANNED);
            }
            // 兜底：status 残留 banned 但已无任何生效处罚，自动恢复
            user.setStatus("active");
            userMapper.updateById(user);
            log.info("用户登录时发现处罚已结束，自动恢复为active: userId={}", user.getId());
        }
    }

    /**
     * 构建登录响应：签发 jti + JWT + 用户信息 + UID
     */
    private LoginResponse buildLoginResponse(User user) {
        String jti = loginSessionManager.issueSession(user.getId());
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole(), jti);

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUser(toUserVO(user));
        response.setNicknameSet(user.getNickname() != null && !user.getNickname().isEmpty());
        response.setUid(UID_BASE + user.getId());
        log.info("用户登录成功: id={}, uid={}, username={}, nicknameSet={}",
                user.getId(), response.getUid(), user.getUsername(), response.getNicknameSet());
        return response;
    }

    private UserVO toUserVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        // UID = 10000 + id，对外账号，替代 username 展示
        vo.setUid(UID_BASE + user.getId());
        vo.setUsername(user.getUsername());
        vo.setPhone(maskPhone(user.getPhone()));
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setAnonymousName(user.getAnonymousName());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setTokenBalance(user.getTokenBalance());
        vo.setTotalFirefly(user.getTotalFirefly());
        vo.setFireflyBalance(user.getFireflyBalance());
        vo.setTotalSignDays(user.getTotalSignDays());
        
        // 从punishment表获取当前生效的处罚信息
        Punishment currentPunishment = punishmentService.getLatestActiveByUserId(user.getId());
        if (currentPunishment != null) {
            vo.setMuteType(convertToLowerCaseType(currentPunishment.getType()));
            vo.setMuteEndTime(currentPunishment.getEndAt());
        } else {
            vo.setMuteType(null);
            vo.setMuteEndTime(null);
        }
        return vo;
    }
    
    /**
     * 手机号脱敏：138****5678
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    private String convertToLowerCaseType(String punishmentType) {
        switch (punishmentType) {
            case Punishment.TYPE_WARNING: return "warning";
            case Punishment.TYPE_MUTE_24H: return "mute_24h";
            case Punishment.TYPE_MUTE_7D: return "mute_7d";
            case Punishment.TYPE_BAN: return "ban";
            default: return punishmentType != null ? punishmentType.toLowerCase() : null;
        }
    }
}
