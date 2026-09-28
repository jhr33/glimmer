package com.glimmer.service.impl;

import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.config.AliyunSmsConfig;
import com.glimmer.service.CaptchaService;
import com.glimmer.service.SmsService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 短信验证码服务实现（阿里云号码认证服务 - 短信认证）
 * <p>
 * 使用通用 SDK CommonRequest 调用 dypnsapi 的两个接口：
 * - SendSmsVerifyCode：验证码由阿里云动态生成并下发（TemplateParam 中 ##code## 为占位符）
 * - CheckSmsVerifyCode：只需手机号 + 用户输入的验证码，由阿里云完成核验
 * <p>
 * 因此我们无需在 Redis 中存储验证码本身，Redis 仅用于发送频控（间隔、日上限）。
 * 注意：该接口为纯服务端 HTTP API，不依赖任何客户端 SDK，H5/5+App 均可使用。
 */
@Slf4j
@Service
public class SmsServiceImpl implements SmsService {

    /** Redis key：发送间隔限制（60秒内只能发一次） */
    private static final String KEY_INTERVAL = "glimmer:sms:interval:%s";
    /** Redis key：每日发送上限 */
    private static final String KEY_DAILY = "glimmer:sms:daily:%s:%s";
    /** Redis key：业务防重标记（注册/绑定流程中校验通过后置位，防止同一验证码跨接口重放） */
    private static final String KEY_VERIFIED = "glimmer:sms:verified:%s:%s";

    private static final String DOMAIN = "dypnsapi.aliyuncs.com";
    private static final String API_VERSION = "2017-05-25";
    /** 验证码长度（阿里云默认4位，项目统一用6位） */
    private static final int CODE_LENGTH = 6;

    private final AliyunSmsConfig smsConfig;
    private final StringRedisTemplate redisTemplate;
    private final CaptchaService captchaService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private IAcsClient acsClient;

    public SmsServiceImpl(AliyunSmsConfig smsConfig, StringRedisTemplate redisTemplate,
                          CaptchaService captchaService) {
        this.smsConfig = smsConfig;
        this.redisTemplate = redisTemplate;
        this.captchaService = captchaService;
    }

    @PostConstruct
    public void init() {
        // 号码认证服务仅在 cn-hangzhou 区域提供
        DefaultProfile profile = DefaultProfile.getProfile(
                "cn-hangzhou",
                smsConfig.getAccessKeyId(),
                smsConfig.getAccessKeySecret());
        this.acsClient = new DefaultAcsClient(profile);
    }

    @Override
    public void sendCode(String phone, String scene, String captchaId, String captcha) {
        // 0. 人机校验：图片验证码（防脚本刷短信）
        if (!captchaService.verifyCaptcha(captchaId, captcha)) {
            throw new BusinessException(ErrorCode.SMS_CAPTCHA_INVALID);
        }

        // 1. 发送间隔限制（60秒内只能发一次）
        String intervalKey = String.format(KEY_INTERVAL, phone);
        Boolean intervalOk = redisTemplate.opsForValue()
                .setIfAbsent(intervalKey, "1", Duration.ofSeconds(smsConfig.getSendIntervalSeconds()));
        if (Boolean.FALSE.equals(intervalOk)) {
            throw new BusinessException(ErrorCode.SMS_SEND_TOO_FREQUENT);
        }

        // 2. 每日发送上限
        String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.BASIC_ISO_DATE);
        String dailyKey = String.format(KEY_DAILY, phone, today);
        Long dailyCount = redisTemplate.opsForValue().increment(dailyKey);
        if (dailyCount != null && dailyCount == 1L) {
            LocalDateTime endOfDay = LocalDateTime.of(LocalDate.now(ZoneId.of("Asia/Shanghai")), LocalTime.MAX);
            redisTemplate.expireAt(dailyKey, endOfDay.atZone(ZoneId.of("Asia/Shanghai")).toInstant());
        }
        if (dailyCount != null && dailyCount > smsConfig.getDailyLimit()) {
            throw new BusinessException(ErrorCode.SMS_DAILY_LIMIT);
        }

        // 3. 调用阿里云号码认证服务 SendSmsVerifyCode
        // ##code## 为阿里云动态验证码占位符；CodeType=1 纯数字；CodeLength=6 位
        try {
            CommonRequest request = new CommonRequest();
            request.setSysMethod(MethodType.POST);
            request.setSysDomain(DOMAIN);
            request.setSysVersion(API_VERSION);
            request.setSysAction("SendSmsVerifyCode");
            request.putQueryParameter("PhoneNumber", phone);
            request.putQueryParameter("SignName", smsConfig.getSignName());
            request.putQueryParameter("TemplateCode", smsConfig.getTemplateCode());
            request.putQueryParameter("TemplateParam",
                    String.format("{\"code\":\"##code##\",\"min\":\"%d\"}", smsConfig.getCodeExpireMinutes()));
            request.putQueryParameter("CodeType", "1");
            request.putQueryParameter("CodeLength", String.valueOf(CODE_LENGTH));
            request.putQueryParameter("ValidTime",
                    String.valueOf(smsConfig.getCodeExpireMinutes() * 60));
            // 重复发送时旧验证码失效（覆盖策略）
            request.putQueryParameter("DuplicatePolicy", "1");

            CommonResponse response = acsClient.getCommonResponse(request);
            JsonNode body = objectMapper.readTree(response.getData());

            String respCode = body.path("Code").asText();
            if (!"OK".equals(respCode)) {
                String respMsg = body.path("Message").asText();
                log.error("短信发送失败: phone={}, code={}, message={}", phone, respCode, respMsg);
                // 发送失败，清除间隔锁，允许用户立即重试
                redisTemplate.delete(intervalKey);
                throw new BusinessException(ErrorCode.SMS_SEND_FAILED,
                        "短信发送失败(" + respCode + ")，请稍后再试");
            }

            log.info("短信发送成功: phone={}, scene={}, bizId={}",
                    phone, scene, body.path("Model").path("BizId").asText());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("短信发送异常: phone={}, error={}", phone, e.getMessage(), e);
            redisTemplate.delete(intervalKey);
            throw new BusinessException(ErrorCode.SMS_SEND_FAILED);
        }
    }

    @Override
    public void verifyCode(String phone, String scene, String code) {
        // 防重放：同一业务场景校验成功后，该验证码立即失效，不能再次用于注册/绑定
        String verifiedKey = String.format(KEY_VERIFIED, scene, phone);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(verifiedKey))) {
            throw new BusinessException(ErrorCode.SMS_CODE_INVALID);
        }

        // 调用阿里云 CheckSmsVerifyCode 核验（只需手机号 + 验证码，阿里云内部关联最新一条）
        try {
            CommonRequest request = new CommonRequest();
            request.setSysMethod(MethodType.POST);
            request.setSysDomain(DOMAIN);
            request.setSysVersion(API_VERSION);
            request.setSysAction("CheckSmsVerifyCode");
            request.putQueryParameter("PhoneNumber", phone);
            request.putQueryParameter("VerifyCode", code);

            CommonResponse response = acsClient.getCommonResponse(request);
            JsonNode body = objectMapper.readTree(response.getData());

            // Code=OK 仅表示接口调用成功，核验结果必须以 Model.VerifyResult 为准
            String verifyResult = body.path("Model").path("VerifyResult").asText();
            if (!"PASS".equals(verifyResult)) {
                String respCode = body.path("Code").asText();
                String respMsg = body.path("Message").asText();
                log.warn("验证码核验未通过: phone={}, scene={}, code={}, result={}, message={}",
                        phone, scene, respCode, verifyResult, respMsg);
                throw new BusinessException(ErrorCode.SMS_CODE_INVALID);
            }

            // 核验通过：置防重标记，有效期与验证码有效期一致
            redisTemplate.opsForValue().set(verifiedKey, "1",
                    Duration.ofMinutes(smsConfig.getCodeExpireMinutes()));
            log.info("验证码核验成功: phone={}, scene={}", phone, scene);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("验证码核验异常: phone={}, error={}", phone, e.getMessage(), e);
            throw new BusinessException(ErrorCode.SMS_CODE_INVALID);
        }
    }
}
