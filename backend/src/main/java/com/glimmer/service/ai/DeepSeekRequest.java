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
}
