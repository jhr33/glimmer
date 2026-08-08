package com.glimmer.task;

import com.glimmer.service.PunishmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 处罚单过期定时任务
 * 扫描 punishment 表中 end_at < NOW() 且 status = ACTIVE 的记录，自动更新为 EXPIRED
 */
@Slf4j
@Component
public class PunishmentExpireTask {

    private final PunishmentService punishmentService;

    public PunishmentExpireTask(PunishmentService punishmentService) {
        this.punishmentService = punishmentService;
    }

    /**
     * 每分钟执行一次，检查过期处罚
     */
    @Scheduled(cron = "0 * * * * ?")
    public void expirePunishments() {
        try {
            punishmentService.expirePunishments();
        } catch (Exception e) {
            log.error("处罚过期定时任务执行失败", e);
        }
    }
}
