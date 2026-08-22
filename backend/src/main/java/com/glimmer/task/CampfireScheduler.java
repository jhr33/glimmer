package com.glimmer.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.glimmer.entity.Campfire;
import com.glimmer.entity.CampfireMember;
import com.glimmer.mapper.CampfireMapper;
import com.glimmer.mapper.CampfireMemberMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 篝火定时任务
 * <p>
 * 1. 熄灭空闲超过 30 分钟的非默认篝火
 * 2. 清理 10 分钟无活动的成员（自动退出）
 * <p>
 * 篝火聊天记录超过 24 小时：
 * - 数据库不主动删除（保留数据便于事后审计、举报处理、申诉取证）
 * - 仅在查询历史接口 CampfireServiceImpl.getHistoryMessages 中过滤
 *   （created_at >= now-24h），前端不显示旧消息
 */
@Slf4j
@Component
public class CampfireScheduler {

    /** 篝火熄灭阈值 */
    private static final int IDLE_MINUTES = 30;
    /** 成员自动退出阈值（离开篝火页面超过此时间自动退出） */
    private static final int MEMBER_IDLE_MINUTES = 20;

    private final CampfireMapper campfireMapper;
    private final CampfireMemberMapper campfireMemberMapper;

    public CampfireScheduler(CampfireMapper campfireMapper,
                             CampfireMemberMapper campfireMemberMapper) {
        this.campfireMapper = campfireMapper;
        this.campfireMemberMapper = campfireMemberMapper;
    }

    /**
     * 每 5 分钟检查一次：无成员、且超过 IDLE_MINUTES 未活动的非默认篝火自动熄灭
     * 默认篝火（type=default）永不熄灭，始终作为广场入口
     */
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
                    new LambdaUpdateWrapper<CampfireMember>()
                            .in(CampfireMember::getCampfireId, idleCampfireIds));
        }

        log.info("自动熄灭空闲篝火: count={}, ids={}", updated, idleCampfireIds);
    }

    /**
     * 每 2 分钟检查一次：清理 10 分钟无活动的成员（自动退出）
     * 用户离开篝火页面超过 10 分钟，视为离线，从成员表移除
     */
    @Scheduled(fixedRate = 120000)
    public void autoRemoveInactiveMembers() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(MEMBER_IDLE_MINUTES);

        // 查询最后活跃时间过期的成员
        int removed = campfireMemberMapper.delete(
                new LambdaQueryWrapper<CampfireMember>()
                        .lt(CampfireMember::getLastActiveAt, threshold));

        // 同时清理 lastActiveAt 为 null 的成员（历史数据兼容）
        int nullRemoved = campfireMemberMapper.delete(
                new LambdaQueryWrapper<CampfireMember>()
                        .isNull(CampfireMember::getLastActiveAt)
                        .lt(CampfireMember::getJoinedAt, threshold));

        int totalRemoved = removed + nullRemoved;
        if (totalRemoved > 0) {
            log.info("清理无活动篝火成员: removed={}, expiredAt={}", totalRemoved, threshold);
        }
    }
}
