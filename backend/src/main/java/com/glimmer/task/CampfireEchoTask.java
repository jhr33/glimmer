package com.glimmer.task;

import com.glimmer.service.EchoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 回音机器人：篝火定时任务
 * 每 10 秒扫描一次活跃篝火：
 * - 用户让回音闭嘴 → 暂时不接话
 * - 用户呼唤回音 → 恢复正常接话
 * - 只有1人在线 → 直接接话
 * - 多人在线但3分钟内只有同一人发言 → 接话
 * - 无人说话超过15分钟 → 主动抛出新话题
 */
@Slf4j
@Component
public class CampfireEchoTask {

    /** 冷场判定秒数：无人发言超过此时间则提新话题（15 分钟） */
    private static final int COLD_SECONDS = 900;

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
