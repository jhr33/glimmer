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
}
