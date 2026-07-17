package com.glimmer.service.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DeepSeek 消息（role + content）
 * 见开发文档 §8.1
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeepSeekMessage {

    /** 角色: system / user / assistant */
    private String role;

    private String content;
}
