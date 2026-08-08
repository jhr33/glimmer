package com.glimmer.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.glimmer.entity.Campfire;
import com.glimmer.entity.CampfireMember;
import com.glimmer.entity.CampfireMessage;
import com.glimmer.entity.Report;
import com.glimmer.mapper.CampfireMapper;
import com.glimmer.mapper.CampfireMemberMapper;
import com.glimmer.mapper.CampfireMessageMapper;
import com.glimmer.mapper.ReportMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CampfireScheduler {

    private static final int IDLE_MINUTES = 30;
    /** 篝火聊天记录保留时长：24 小时 */
    private static final int MESSAGE_RETENTION_HOURS = 24;

    private final CampfireMapper campfireMapper;
    private final CampfireMemberMapper campfireMemberMapper;
    private final CampfireMessageMapper campfireMessageMapper;
    private final ReportMapper reportMapper;

    public CampfireScheduler(CampfireMapper campfireMapper,
                            CampfireMemberMapper campfireMemberMapper,
                            CampfireMessageMapper campfireMessageMapper,
                            ReportMapper reportMapper) {
        this.campfireMapper = campfireMapper;
        this.campfireMemberMapper = campfireMemberMapper;
        this.campfireMessageMapper = campfireMessageMapper;
        this.reportMapper = reportMapper;
    }

    @Scheduled(fixedRate = 300000)
    public void autoExtinguishIdleCampfires() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(IDLE_MINUTES);
        
        List<Campfire> activeCampfires = campfireMapper.selectList(
                new LambdaQueryWrapper<Campfire>()
                        .eq(Campfire::getStatus, "active")
                        .ne(Campfire::getType, "default"));

        if (activeCampfires.isEmpty()) {
            return;
        }

        List<Long> campfireIds = activeCampfires.stream()
                .map(Campfire::getId)
                .collect(Collectors.toList());

        List<CampfireMember> members = campfireMemberMapper.selectList(
                new LambdaQueryWrapper<CampfireMember>()
                        .in(CampfireMember::getCampfireId, campfireIds));

        Map<Long, Long> memberCountMap = members.stream()
                .collect(Collectors.groupingBy(
                        CampfireMember::getCampfireId,
                        Collectors.counting()));

        List<Long> idleCampfireIds = activeCampfires.stream()
                .filter(c -> {
                    long memberCount = memberCountMap.getOrDefault(c.getId(), 0L);
                    if (memberCount > 0) {
                        return false;
                    }
                    LocalDateTime lastActiveAt = c.getLastActiveAt();
                    if (lastActiveAt == null) {
                        lastActiveAt = c.getCreatedAt();
                    }
                    return lastActiveAt.isBefore(threshold);
                })
                .map(Campfire::getId)
                .collect(Collectors.toList());

        if (idleCampfireIds.isEmpty()) {
            return;
        }

        int updated = campfireMapper.update(null,
                new LambdaUpdateWrapper<Campfire>()
                        .in(Campfire::getId, idleCampfireIds)
                        .set(Campfire::getStatus, "extinguished"));

        if (updated > 0) {
            campfireMemberMapper.delete(
                    new LambdaQueryWrapper<CampfireMember>()
                            .in(CampfireMember::getCampfireId, idleCampfireIds));
        }

        log.info("自动熄灭空闲篝火: count={}, ids={}", updated, idleCampfireIds);
    }

    /**
     * 每小时清理一次超过 24 小时的篝火聊天记录
     * 排除存在待审核举报（status=pending）的消息，避免删除后导致管理员审核时找不到目标内容
     */
    @Scheduled(fixedRate = 3600000)
    public void autoCleanupStaleMessages() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(MESSAGE_RETENTION_HOURS);

        // 查询存在待审核举报的篝火消息ID，避免删除后破坏举报审核流程（getTargetUserId 会抛 NOT_FOUND）
        List<Report> pendingReports = reportMapper.selectList(
                new LambdaQueryWrapper<Report>()
                        .eq(Report::getTargetType, "campfire_message")
                        .eq(Report::getStatus, "pending"));
        Set<Long> protectedIds = pendingReports.stream()
                .map(Report::getTargetId)
                .collect(Collectors.toSet());

        LambdaQueryWrapper<CampfireMessage> wrapper = new LambdaQueryWrapper<CampfireMessage>()
                .lt(CampfireMessage::getCreatedAt, threshold);
        if (!protectedIds.isEmpty()) {
            wrapper.notIn(CampfireMessage::getId, protectedIds);
        }

        int deleted = campfireMessageMapper.delete(wrapper);
        if (deleted > 0) {
            log.info("自动清理超时篝火聊天记录: count={}, threshold={}", deleted, threshold);
        }
    }
}
