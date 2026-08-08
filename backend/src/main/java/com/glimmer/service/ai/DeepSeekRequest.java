package com.glimmer.service.ai;

import lombok.Data;

import java.util.List;

/**
 * DeepSeek 请求体
 * 见开发文档 §8.1
 */
@Data
public class DeepSeekRequest {

    private String model;

    private List<DeepSeekMessage> messages;

    /** 是否流式，默认 false */
    private Boolean stream;

    /**
     * 流式选项：仅当 stream=true 时生效。
     * 设置 include_usage=true 后，流式响应的最后一个 chunk 会携带 usage 字段，
     * 用于 token 计费统计。
     */
    private StreamOptions streamOptions;

    @Data
    public static class StreamOptions {
        /** 是否在最后一个 chunk 返回 usage */
        private Boolean includeUsage;
    }
}
