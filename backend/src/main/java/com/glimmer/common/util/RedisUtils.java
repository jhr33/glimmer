package com.glimmer.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * Redis 工具类：封装常用操作（计数器、Set、过期等）
 * 所有 key 统一前缀 "glimmer:"，避免与其他项目冲突
 */
@Slf4j
@Component
public class RedisUtils {

    private static final String KEY_PREFIX = "glimmer:";

    private final StringRedisTemplate redis;

    public RedisUtils(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // ==================== 基础 Key 操作 ====================

    public String fullKey(String key) {
        return KEY_PREFIX + key;
    }

    /**
     * 设置带 TTL 的字符串值
     */
    public void set(String key, String value, Duration ttl) {
        try {
            redis.opsForValue().set(fullKey(key), value, ttl);
        } catch (Exception e) {
            log.warn("[Redis] set 失败 key={}, err={}", key, e.getMessage());
        }
    }

    /**
     * 获取字符串值
     */
    public String get(String key) {
        try {
            return redis.opsForValue().get(fullKey(key));
        } catch (Exception e) {
            log.warn("[Redis] get 失败 key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 删除 key
     */
    public Boolean delete(String key) {
        try {
            return redis.delete(fullKey(key));
        } catch (Exception e) {
            log.warn("[Redis] delete 失败 key={}, err={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 判断 key 是否存在
     */
    public Boolean exists(String key) {
        try {
            return redis.hasKey(fullKey(key));
        } catch (Exception e) {
            log.warn("[Redis] exists 失败 key={}, err={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 设置 TTL
     */
    public void expire(String key, Duration ttl) {
        try {
            redis.expire(fullKey(key), ttl);
        } catch (Exception e) {
            log.warn("[Redis] expire 失败 key={}, err={}", key, e.getMessage());
        }
    }

    // ==================== 计数器操作 ====================

    /**
     * 自增 1（key 不存在时自动创建为 1）
     *
     * @return 自增后的值
     */
    public Long incr(String key) {
        try {
            return redis.opsForValue().increment(fullKey(key));
        } catch (Exception e) {
            log.warn("[Redis] incr 失败 key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 自增并设置 TTL（仅当 key 是新建时才设置 TTL，避免每次自增都重置过期）
     */
    public Long incrWithTtl(String key, Duration ttl) {
        try {
            Long count = redis.opsForValue().increment(fullKey(key));
            if (count != null && count == 1L) {
                redis.expire(fullKey(key), ttl);
            }
            return count;
        } catch (Exception e) {
            log.warn("[Redis] incrWithTtl 失败 key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 自减 1（不会低于 0）
     */
    public Long decr(String key) {
        try {
            Long count = redis.opsForValue().increment(fullKey(key), -1);
            if (count != null && count < 0) {
                // 修正为 0，防止误减
                redis.opsForValue().set(fullKey(key), "0");
                count = 0L;
            }
            return count;
        } catch (Exception e) {
            log.warn("[Redis] decr 失败 key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 获取计数器当前值
     */
    public long getCount(String key) {
        try {
            String val = redis.opsForValue().get(fullKey(key));
            if (val == null) return 0L;
            return Long.parseLong(val);
        } catch (Exception e) {
            log.warn("[Redis] getCount 失败 key={}, err={}", key, e.getMessage());
            return 0L;
        }
    }

    // ==================== Set 操作 ====================

    /**
     * 向 Set 添加成员，并设置 TTL（仅首次添加时）
     */
    public Long saddWithTtl(String key, Duration ttl, String... members) {
        try {
            Long added = redis.opsForSet().add(fullKey(key), members);
            if (added != null && added > 0) {
                // 幂等设置过期（已存在 key 不会重置 TTL）
                redis.expire(fullKey(key), ttl);
            }
            return added;
        } catch (Exception e) {
            log.warn("[Redis] saddWithTtl 失败 key={}, err={}", key, e.getMessage());
            return 0L;
        }
    }

    /**
     * 获取 Set 大小
     */
    public long sCard(String key) {
        try {
            Long size = redis.opsForSet().size(fullKey(key));
            return size != null ? size : 0L;
        } catch (Exception e) {
            log.warn("[Redis] sCard 失败 key={}, err={}", key, e.getMessage());
            return 0L;
        }
    }

    /**
     * 获取 Set 全部成员
     */
    public Set<String> sMembers(String key) {
        try {
            return redis.opsForSet().members(fullKey(key));
        } catch (Exception e) {
            log.warn("[Redis] sMembers 失败 key={}, err={}", key, e.getMessage());
            return Set.of();
        }
    }

    /**
     * 判断 member 是否在 Set 中
     */
    public Boolean sIsMember(String key, String member) {
        try {
            return redis.opsForSet().isMember(fullKey(key), member);
        } catch (Exception e) {
            log.warn("[Redis] sIsMember 失败 key={}, err={}", key, e.getMessage());
            return false;
        }
    }

    // ==================== TTL 工具 ====================

    /**
     * 计算到次日 0 点（Asia/Shanghai）的剩余时间
     */
    public static Duration ttlUntilEndOfDay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);
        long seconds = ChronoUnit.SECONDS.between(now, endOfDay) + 1; // +1 秒保证覆盖到 23:59:59
        return Duration.ofSeconds(seconds);
    }

    /**
     * 计算到指定秒数后的 Duration
     */
    public static Duration ttlOfSeconds(long seconds) {
        return Duration.ofSeconds(seconds);
    }

    // ==================== 降级保护 ====================

    /**
     * Redis 是否可用（ping）
     */
    public boolean isAvailable() {
        try {
            return "PONG".equals(redis.getConnectionFactory().getConnection().ping());
        } catch (Exception e) {
            return false;
        }
    }
}
