package com.glimmer.task;

import com.glimmer.service.EchoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 回音机器人：漂流瓶自动回复定时任务
 * 每 30 秒扫描一次未回复的漂流瓶，调用 AI 生成回复
 * 见开发文档 §9 AI 机器人系统
 */
@Slf4j
@Component
public class BottleEchoTask {

    /** 每次最多处理的瓶子数量 */
    private static final int BATCH_SIZE = 5;

    private final EchoService echoService;

    public BottleEchoTask(EchoService echoService) {
        this.echoService = echoService;
    }

    /**
     * 每 30 秒执行一次
     */
    @Scheduled(fixedRate = 30000)
    public void scanAndReplyBottles() {
        if (!echoService.isAutoModeEnabled()) {
            return;
        }

        log.debug("[回音漂流瓶] 开始扫描...");
        try {
            int replied = echoService.processPendingBottles(BATCH_SIZE);
            if (replied > 0) {
                log.info("[回音漂流瓶] 本次自动回复了 {} 个漂流瓶", replied);
            } else {
                log.debug("[回音漂流瓶] 无待回复瓶子");
            }
        } catch (Exception e) {
            log.error("[回音漂流瓶] 自动回复异常", e);
        }
    }
}
