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

@Slf4j
@Component
public class CampfireScheduler {

    private static final int IDLE_MINUTES = 30;

    private final CampfireMapper campfireMapper;
    private final CampfireMemberMapper campfireMemberMapper;

    public CampfireScheduler(CampfireMapper campfireMapper,
                            CampfireMemberMapper campfireMemberMapper) {
        this.campfireMapper = campfireMapper;
        this.campfireMemberMapper = campfireMemberMapper;
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
}
