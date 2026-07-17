package com.glimmer.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.glimmer.entity.AiConversation;
import com.glimmer.mapper.AiConversationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 会话超时定时任务
 * 见开发文档 §3.4.5
 * - 每分钟扫描 status='active' 且 last_active_at 距今 >= 3 小时的记录，更新为 timeout
 */
@Slf4j
@Component
public class AiConversationTimeoutTask {

    /** 超时阈值：3 小时 */
    private static final int TIMEOUT_HOURS = 3;

    private final AiConversationMapper aiConversationMapper;

    public AiConversationTimeoutTask(AiConversationMapper aiConversationMapper) {
        this.aiConversationMapper = aiConversationMapper;
    }

    /**
     * 每分钟执行一次
     */
    @Scheduled(fixedRate = 60000)
    public void timeoutConversations() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(TIMEOUT_HOURS);
        List<AiConversation> timeoutList = aiConversationMapper.selectList(
                new LambdaQueryWrapper<AiConversation>()
                        .eq(AiConversation::getStatus, "active")
                        .lt(AiConversation::getLastActiveAt, threshold));

        if (timeoutList.isEmpty()) {
            return;
        }

        List<Long> ids = timeoutList.stream().map(AiConversation::getId).toList();
        int updated = aiConversationMapper.update(null,
                new LambdaUpdateWrapper<AiConversation>()
                        .in(AiConversation::getId, ids)
                        .set(AiConversation::getStatus, "timeout"));
        log.info("AI 会话超时关闭: count={}, ids={}", updated, ids);
    }
}
