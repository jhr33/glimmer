package com.glimmer.config.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DeepSeek 配置属性
 * glimmer.deepseek.*
 * 见开发文档 §3.4.4 / §8.3
 */
@Data
@Component
@ConfigurationProperties(prefix = "glimmer.deepseek")
public class DeepSeekProperties {

    /** DeepSeek API 地址 */
    private String apiUrl = "https://api.deepseek.com/v1/chat/completions";

    /** API Key（部署时通过环境变量 DEEPSEEK_API_KEY 注入） */
    private String apiKey;

    /** 模型名称，默认 deepseek-v3 */
    private String model = "deepseek-v3";

    /** 上下文消息最大条数（见开发文档 §3.4.4） */
    private int maxContextMessages = 20;

    /** 系统提示词 */
    private String systemPrompt = "你是 glimmer 网站的温暖倾听者，用温柔、有同理心的语言陪伴用户。回答要有共情力，避免说教，鼓励用户表达感受。";

    /** 摘要+关键信息提取 prompt（解锁时调用，%s 为对话内容） */
    private String summaryPrompt =
        "请分析以下对话，提取信息。严格返回JSON格式（不要markdown代码块、不要多余文字）：\n" +
        "{\"summary\":\"2-3句话摘要，包含重要事情、情绪状态、关键信息\",\"keyInfo\":{\"字段名\":\"值\"}}\n" +
        "要求：\n" +
        "- 摘要2-3句话，关注重要事情、情绪状态、关键信息，忽略琐碎聊天\n" +
        "- keyInfo提取3-5个关键信息项，字段名用英文简写，值用中文\n" +
        "- 示例：{\"summary\":\"用户正在准备考研，最近压力大，家里养了一只猫\",\"keyInfo\":{\"studying\":\"考研\",\"pet\":\"猫\",\"mood\":\"压力大\"}}\n\n" +
        "对话内容：\n%s";

    /** 每次解锁增加的轮次 */
    private int unlockQuotaStep = 10;

    /** 免费会话每日轮次上限 */
    private int freeDailyQuota = 10;
}
