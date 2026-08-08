package com.glimmer.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 轻量级分布式锁（基于 Redis SET NX EX + Lua 原子释放）
 * <p>
 * 设计要点：
 * 1. 获取锁使用 SET key token NX EX ttl（原子操作，避免 set+expire 之间的崩溃风险）
 * 2. 释放锁使用 Lua 脚本校验 token 后删除（避免误删别人的锁）
 * 3. 获取锁失败（Redis 不可用或竞争超时）时降级直跑，由业务层乐观锁（@Version）兜底正确性
 * 4. 所有 key 统一前缀 "glimmer:lock:"，避免与其他模块冲突
 * <p>
 * 注意：本锁为短临界区优化用途（如代币扣减的 read-modify-write），
 * 锁会在事务提交前释放，因此 @Version 乐观锁仍是最终正确性保障。
 */
@Slf4j
@Component
public class DistributedLock {

    private static final String KEY_PREFIX = "glimmer:lock:";

    /** 释放锁的 Lua 脚本：仅当 key 对应的 value 等于 token 时才删除（CAS） */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setScriptText(
                "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end");
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    /** 自旋获取锁的休眠间隔（毫秒） */
    private static final long SPIN_INTERVAL_MS = 50L;

    private final StringRedisTemplate redis;

    public DistributedLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private String fullKey(String key) {
        return KEY_PREFIX + key;
    }

    /**
     * 尝试获取分布式锁（自旋等待）。
     *
     * @param key      业务 key（不含前缀）
     * @param ttl      锁持有过期时间（防止持锁进程崩溃导致死锁）
     * @param waitTime 最大等待获取时间
     * @return 锁 token（用于安全释放）；获取失败返回 null
     */
    public String tryLock(String key, Duration ttl, Duration waitTime) {
        try {
            String token = UUID.randomUUID().toString().replace("-", "");
            long deadline = System.currentTimeMillis() + waitTime.toMillis();
            do {
                Boolean ok = redis.opsForValue().setIfAbsent(fullKey(key), token, ttl);
                if (Boolean.TRUE.equals(ok)) {
                    return token;
                }
                try {
                    Thread.sleep(SPIN_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            } while (System.currentTimeMillis() < deadline);
            return null;
        } catch (Exception e) {
            log.warn("[Redis] tryLock 失败 key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 释放锁（Lua 原子校验 token 后删除，避免误删他人持有的锁）。
     */
    public void unlock(String key, String token) {
        try {
            redis.execute(UNLOCK_SCRIPT, Collections.singletonList(fullKey(key)), token);
        } catch (Exception e) {
            log.warn("[Redis] unlock 失败 key={}, err={}", key, e.getMessage());
        }
    }

    /**
     * 带分布式锁执行 Supplier。
     * <p>
     * 获取锁失败（Redis 不可用或竞争超时）时降级直跑，由业务层乐观锁兜底正确性，
     * 保证服务可用性优先。
     * <p>
     * 释放时机：action 执行完毕后立即释放（非延迟到事务提交后）。
     * 设计权衡：曾尝试将释放延迟到 afterCompletion 以串行化事务，但 Spring 事务生命周期中
     * afterCompletion 在 DB 连接释放之前执行，持锁线程的 Redis unlock 与等待者的自旋都会
     * 占用 DB 连接，在小连接池（HikariCP max 10）下易耗尽连接池导致全局超时。
     * 立即释放使锁仅持有临界区（毫秒级），等待者几乎瞬间获取，不长时间占用连接；
     * 最终一致性由 @Version 乐观锁兜底。
     *
     * @param key      业务 key
     * @param ttl      锁过期时间
     * @param waitTime 获取锁最大等待时间
     * @param action   临界区逻辑
     * @return action 的返回值
     */
    public <T> T executeWithLock(String key, Duration ttl, Duration waitTime, Supplier<T> action) {
        String token = tryLock(key, ttl, waitTime);
        if (token == null) {
            // 降级：Redis 不可用或竞争超时，直跑（乐观锁 @Version 兜底）
            log.warn("[Redis] 获取分布式锁失败，降级直跑 key={}", key);
            return action.get();
        }
        try {
            return action.get();
        } finally {
            unlock(key, token);
        }
    }
}
