package com.glimmer.service.ai;

import lombok.Data;

import java.util.List;

/**
 * DeepSeek 响应体
 * 见开发文档 §8.1
 */
@Data
public class DeepSeekResponse {

    private List<Choice> choices;

    private Usage usage;

    @Data
    public static class Choice {
        private Integer index;
        private Message message;
        private String finishReason;
    }

    @Data
    public static class Message {
        private String role;
        private String content;
    }

    @Data
    public static class Usage {
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
    }
}
