package com.glimmer.service.impl;

import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.util.RedisUtils;
import com.glimmer.entity.Punishment;
import com.glimmer.entity.User;
import com.glimmer.mapper.PunishmentMapper;
import com.glimmer.mapper.UserMapper;
import com.glimmer.service.PunishmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 处罚单服务实现类
 */
@Slf4j
@Service
public class PunishmentServiceImpl implements PunishmentService {

    /** 封禁状态缓存 key：user:ban:{userId}，值为 "1"=封禁 / "0"=未封禁 */
    private static final String CACHE_KEY_USER_BANNED = "user:ban:%d";
    /** 永久封禁缓存 key：user:perm_ban:{userId}，值为 "1"=永久封禁 / "0"=非永久封禁 */
    private static final String CACHE_KEY_USER_PERM_BANNED = "user:perm_ban:%d";
    /** 封禁状态缓存 TTL：5 分钟（处罚变更时主动失效） */
    private static final Duration BANNED_CACHE_TTL = Duration.ofMinutes(5);

    private final PunishmentMapper punishmentMapper;
    private final UserMapper userMapper;
    private final RedisUtils redis;

    public PunishmentServiceImpl(PunishmentMapper punishmentMapper, UserMapper userMapper, RedisUtils redis) {
        this.punishmentMapper = punishmentMapper;
        this.userMapper = userMapper;
        this.redis = redis;
    }

    /**
     * 清除用户封禁状态缓存（同时清除限制发言缓存和永久封禁缓存）
     */
    private void evictBannedCache(Long userId) {
        redis.delete(String.format(CACHE_KEY_USER_BANNED, userId));
        redis.delete(String.format(CACHE_KEY_USER_PERM_BANNED, userId));
    }

    @Override
    @Transactional
    public Punishment createPunishment(Long userId, String type, String reason, String sourceType, Long sourceId) {
        Punishment punishment = new Punishment();
        punishment.setUserId(userId);
        punishment.setType(type);
        punishment.setReason(reason);
        punishment.setSourceType(sourceType);
        punishment.setSourceId(sourceId);
        punishment.setStartAt(LocalDateTime.now());
        
        // 设置结束时间
        LocalDateTime endAt = null;
        switch (type) {
            case Punishment.TYPE_MUTE_24H:
                endAt = LocalDateTime.now().plusHours(24);
                break;
            case Punishment.TYPE_MUTE_7D:
                endAt = LocalDateTime.now().plusDays(7);
                break;
            case Punishment.TYPE_BAN:
                endAt = null; // 永久封禁无结束时间
                break;
            case Punishment.TYPE_WARNING:
                endAt = null; // 警告无结束时间
                break;
        }
        punishment.setEndAt(endAt);
        punishment.setStatus(Punishment.STATUS_ACTIVE);
        
        punishmentMapper.insert(punishment);
        log.info("创建处罚单: id={}, userId={}, type={}, sourceType={}, sourceId={}", 
                punishment.getId(), userId, type, sourceType, sourceId);

        // 创建非WARNING处罚时，同步将用户状态设为banned
        if (!Punishment.TYPE_WARNING.equals(type)) {
            User user = userMapper.selectById(userId);
            if (user != null && !"banned".equals(user.getStatus())) {
                user.setStatus("banned");
                userMapper.updateById(user);
                log.info("创建处罚同步用户状态为banned: userId={}, punishmentType={}", userId, type);
            }
            // 主动失效封禁状态缓存
            evictBannedCache(userId);
        }

        return punishment;
    }

    @Override
    public Punishment getById(Long id) {
        return punishmentMapper.selectById(id);
    }

    @Override
    public List<Punishment> getActiveByUserId(Long userId) {
        return punishmentMapper.selectActiveByUserId(userId);
    }

    @Override
    public Punishment getLatestActiveByUserId(Long userId) {
        return punishmentMapper.selectLatestActiveByUserId(userId);
    }

    @Override
    public List<Punishment> getBySource(Long sourceId, String sourceType) {
        return punishmentMapper.selectBySource(sourceId, sourceType);
    }

    @Override
    @Transactional
    public void revokePunishment(Long id) {
        Punishment punishment = punishmentMapper.selectById(id);
        if (punishment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "处罚单不存在");
        }
        if (Punishment.STATUS_REVOKED.equals(punishment.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "处罚单已被撤销");
        }
        
        punishment.setStatus(Punishment.STATUS_REVOKED);
        punishmentMapper.updateById(punishment);
        
        Long userId = punishment.getUserId();
        log.info("撤销处罚单: id={}, userId={}, type={}", id, userId, punishment.getType());
        
        // 检查用户是否还有其他生效的处罚（排除WARNING类型）
        List<Punishment> remainingActive = punishmentMapper.selectActiveByUserId(userId);
        boolean hasActivePunishment = remainingActive.stream()
                .anyMatch(p -> !Punishment.TYPE_WARNING.equals(p.getType()));
        
        // 如果没有其他生效处罚，恢复用户状态为active
        if (!hasActivePunishment) {
            User user = userMapper.selectById(userId);
            if (user != null && "banned".equals(user.getStatus())) {
                user.setStatus("active");
                userMapper.updateById(user);
                log.info("用户处罚全部解除，状态恢复为active: userId={}", userId);
            }
        }
        // 主动失效封禁状态缓存
        evictBannedCache(userId);
    }

    @Override
    @Transactional
    public void expirePunishments() {
        List<Punishment> expiredList = punishmentMapper.selectExpired(LocalDateTime.now());
        if (expiredList.isEmpty()) {
            return;
        }
        
        for (Punishment punishment : expiredList) {
            punishment.setStatus(Punishment.STATUS_EXPIRED);
            punishmentMapper.updateById(punishment);
            
            // 检查该用户是否还有其他生效的处罚
            Long userId = punishment.getUserId();
            List<Punishment> remainingActive = punishmentMapper.selectActiveByUserId(userId);
            boolean hasActivePunishment = remainingActive.stream()
                    .anyMatch(p -> !Punishment.TYPE_WARNING.equals(p.getType()));
            
            // 如果没有其他生效处罚，恢复用户状态为active
            if (!hasActivePunishment) {
                User user = userMapper.selectById(userId);
                if (user != null && "banned".equals(user.getStatus())) {
                    user.setStatus("active");
                    userMapper.updateById(user);
                    log.info("用户处罚过期，状态恢复为active: userId={}", userId);
                }
            }
            // 主动失效封禁状态缓存
            evictBannedCache(userId);
        }
        log.info("过期处罚单: 数量={}", expiredList.size());
    }

    @Override
    public boolean isUserBanned(Long userId) {
        String cacheKey = String.format(CACHE_KEY_USER_BANNED, userId);
        // 1. 先查 Redis 缓存
        String cached = redis.get(cacheKey);
        if (cached != null) {
            return "1".equals(cached);
        }
        // 2. 缓存未命中，查 DB
        List<Punishment> activePunishments = punishmentMapper.selectActiveByUserId(userId);
        boolean banned = false;
        for (Punishment p : activePunishments) {
            // WARNING不限制发言，其他类型都限制
            if (!Punishment.TYPE_WARNING.equals(p.getType())) {
                banned = true;
                break;
            }
        }
        // 3. 回填缓存
        redis.set(cacheKey, banned ? "1" : "0", BANNED_CACHE_TTL);
        return banned;
    }

    @Override
    public boolean isUserPermanentlyBanned(Long userId) {
        String cacheKey = String.format(CACHE_KEY_USER_PERM_BANNED, userId);
        // 1. 先查 Redis 缓存
        String cached = redis.get(cacheKey);
        if (cached != null) {
            return "1".equals(cached);
        }
        // 2. 缓存未命中，查 DB：只检查是否有 BAN 类型的生效处罚
        List<Punishment> activePunishments = punishmentMapper.selectActiveByUserId(userId);
        boolean permBanned = activePunishments.stream()
                .anyMatch(p -> Punishment.TYPE_BAN.equals(p.getType()));
        // 3. 回填缓存
        redis.set(cacheKey, permBanned ? "1" : "0", BANNED_CACHE_TTL);
        return permBanned;
    }

    @Override
    public String getUserCurrentPunishmentType(Long userId) {
        Punishment latest = punishmentMapper.selectLatestActiveByUserId(userId);
        if (latest == null) {
            return null;
        }
        return latest.getType();
    }
}