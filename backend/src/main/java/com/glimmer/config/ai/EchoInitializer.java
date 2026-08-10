package com.glimmer.config.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.glimmer.entity.User;
import com.glimmer.mapper.UserMapper;
import com.glimmer.service.EchoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 回音机器人账号初始化
 * 应用启动时自动创建 bot_echo 账号（如不存在）
 * 见开发文档 §9 AI 机器人系统
 */
@Slf4j
@Component
public class EchoInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public EchoInitializer(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        User existing = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, EchoService.BOT_USERNAME));

        if (existing != null) {
            log.info("回音机器人账号已存在: userId={}", existing.getId());
            return;
        }

        User bot = new User();
        bot.setUsername(EchoService.BOT_USERNAME);
        // 默认密码 echo2024，管理员可随时登录修改
        bot.setPassword(passwordEncoder.encode("echo2024"));
        bot.setNickname("回音");
        bot.setAnonymousName("回音");
        bot.setRole(EchoService.ROLE_BOT);
        bot.setStatus("active");
        bot.setTokenBalance(9999);
        bot.setTotalFirefly(0);
        bot.setFireflyBalance(0);

        userMapper.insert(bot);
        log.info("回音机器人账号初始化成功: userId={}, username={}", bot.getId(), EchoService.BOT_USERNAME);
    }
}
