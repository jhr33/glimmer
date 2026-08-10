package com.glimmer.task;

import com.glimmer.service.EchoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 回音机器人：篝火定时任务
 * 每 10 秒扫描一次活跃篝火：
 * - 真人发言后 10 秒无人接话 → 回音回应（保持对话不冷场）
 * - 冷场超过 30 分钟无人发言 → 回音提新话题
 * 见开发文档 §9 AI 机器人系统
 */
@Slf4j
@Component
public class CampfireEchoTask {

    /** 冷场判定秒数：长期无人发言超过此时间则提新话题（30 分钟） */
    private static final int COLD_SECONDS = 1800;

    private final EchoService echoService;

    public CampfireEchoTask(EchoService echoService) {
        this.echoService = echoService;
    }

    /**
     * 每 10 秒执行一次
     */
    @Scheduled(fixedRate = 10000)
    public void scanCampfires() {
        if (!echoService.isAutoModeEnabled()) {
            return;
        }

        log.debug("[回音篝火] 开始扫描活跃篝火...");
        try {
            boolean spoke = echoService.processCampfireColdspot(COLD_SECONDS);
            if (spoke) {
                log.info("[回音篝火] 本次有篝火被救场");
            } else {
                log.debug("[回音篝火] 无需救场");
            }
        } catch (Exception e) {
            log.error("[回音篝火] 救场异常", e);
        }
    }
}
