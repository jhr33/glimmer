package com.glimmer.service.impl;

import com.glimmer.service.CaptchaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * 图片验证码服务实现
 * <p>
 * 生成 4 位随机字母数字验证码，绘制到 PNG 图片，存 Redis（TTL 2 分钟）。
 * 发送短信前校验，防脚本刷短信。
 */
@Slf4j
@Service
public class CaptchaServiceImpl implements CaptchaService {

    private static final String KEY_PREFIX = "glimmer:captcha:";
    private static final Duration TTL = Duration.ofMinutes(2);
    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;
    private static final int CODE_LENGTH = 4;
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // 去掉易混淆字符 0/O/1/I

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom random = new SecureRandom();

    public CaptchaServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String generateCaptcha() {
        // 1. 生成 4 位验证码
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        String captchaId = UUID.randomUUID().toString().replace("-", "");

        // 2. 存 Redis（注意：区分大小写，前端转大写后提交）
        redisTemplate.opsForValue().set(KEY_PREFIX + captchaId, code.toString(), TTL);

        // 3. 生成图片并缓存（与 code 同 key，加 :img 后缀）
        String imageBase64 = drawCaptchaImage(code.toString());
        redisTemplate.opsForValue().set(KEY_PREFIX + captchaId + ":img", imageBase64, TTL);

        return captchaId;
    }

    @Override
    public String getCaptchaImage(String captchaId) {
        return redisTemplate.opsForValue().get(KEY_PREFIX + captchaId + ":img");
    }

    @Override
    public boolean verifyCaptcha(String captchaId, String code) {
        if (captchaId == null || code == null) {
            return false;
        }
        String key = KEY_PREFIX + captchaId;
        String cachedCode = redisTemplate.opsForValue().get(key);
        if (cachedCode == null) {
            return false; // 验证码已过期或不存在
        }
        // 大小写不敏感
        boolean matched = cachedCode.equalsIgnoreCase(code.trim());
        if (matched) {
            // 校验成功后删除，防止重复使用
            redisTemplate.delete(key);
            redisTemplate.delete(key + ":img");
        }
        return matched;
    }

    /**
     * 绘制验证码图片并返回 base64 PNG
     */
    private String drawCaptchaImage(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // 背景色
            g.setColor(new Color(240, 240, 240));
            g.fillRect(0, 0, WIDTH, HEIGHT);

            // 干扰线
            for (int i = 0; i < 6; i++) {
                g.setColor(randomColor(100, 200));
                int x1 = random.nextInt(WIDTH);
                int y1 = random.nextInt(HEIGHT);
                int x2 = random.nextInt(WIDTH);
                int y2 = random.nextInt(HEIGHT);
                g.drawLine(x1, y1, x2, y2);
            }

            // 干扰点
            for (int i = 0; i < 30; i++) {
                g.setColor(randomColor(50, 150));
                g.fillOval(random.nextInt(WIDTH), random.nextInt(HEIGHT), 2, 2);
            }

            // 文字
            g.setFont(new Font("Arial", Font.BOLD, 26));
            for (int i = 0; i < code.length(); i++) {
                g.setColor(randomColor(20, 100));
                int x = 15 + i * 24;
                int y = 30;
                // 轻微旋转
                double theta = (random.nextDouble() - 0.5) * 0.4;
                g.rotate(theta, x, y);
                g.drawString(String.valueOf(code.charAt(i)), x, y);
                g.rotate(-theta, x, y);
            }
        } finally {
            g.dispose();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            log.error("验证码图片生成失败", e);
            return "";
        }
    }

    private Color randomColor(int min, int max) {
        int r = min + random.nextInt(max - min);
        int g = min + random.nextInt(max - min);
        int b = min + random.nextInt(max - min);
        return new Color(r, g, b);
    }
}
