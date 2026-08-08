package com.glimmer.common.util;

import com.glimmer.common.exception.BusinessException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.entity.User;
import com.glimmer.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * 代币余额变更助手：以分布式锁串行化 per-user 的余额 read-modify-write，
 * 配合 User 实体的 @Version 乐观锁兜底，消除并发扣减/增加时的冲突。
 * <p>
 * 降级策略：Redis 不可用时 {@link DistributedLock#executeWithLock} 降级直跑，
 * 由 @Version 乐观锁保证最终正确性。
 */
@Slf4j
@Component
public class TokenBalanceHelper {

    /** 锁持有时间：临界区仅含 selectById + updateById，毫秒级，10s 足够防止崩溃死锁 */
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    /** 获取锁最大等待时间：立即释放策略下锁仅持有毫秒级，等待者几乎瞬间获取；缩短上限以限制最坏情况下的 DB 连接占用 */
    private static final Duration LOCK_WAIT = Duration.ofSeconds(1);
    /** 锁 key：token:lock:{userId} */
    private static final String LOCK_KEY = "token:lock:%d";

    private final UserMapper userMapper;
    private final DistributedLock distributedLock;

    public TokenBalanceHelper(UserMapper userMapper, DistributedLock distributedLock) {
        this.userMapper = userMapper;
        this.distributedLock = distributedLock;
    }

    /**
     * 扣减代币（加分布式锁 + 余额校验 + 乐观锁兜底）。
     * <ul>
     *   <li>用户不存在 → {@link ErrorCode#NOT_FOUND}</li>
     *   <li>余额不足 → {@link ErrorCode#TOKEN_NOT_ENOUGH}</li>
     *   <li>并发冲突 → {@link ErrorCode#CONFLICT}</li>
     * </ul>
     *
     * @return 扣减后的用户实体（已持久化）
     */
    public User deduct(Long userId, int amount) {
        return modifyWithLock(userId, u -> {
            if (u.getTokenBalance() == null || u.getTokenBalance() < amount) {
                throw new BusinessException(ErrorCode.TOKEN_NOT_ENOUGH);
            }
            u.setTokenBalance(u.getTokenBalance() - amount);
        });
    }

    /**
     * 通用：加分布式锁执行用户字段变更（caller 在 modifier 中读写字段）。
     * 加锁内重新读取最新用户数据，变更后用 @Version 乐观锁更新。
     * <p>
     * 适用于需要同时修改多个余额字段的场景（如感谢奖励 +1代币 +1萤火）。
     *
     * @param userId   用户ID
     * @param modifier 字段变更逻辑（在已加锁、已读取最新 user 的上下文中执行）
     * @return 更新后的用户实体（已持久化）
     */
    public User modifyWithLock(Long userId, Consumer<User> modifier) {
        return distributedLock.executeWithLock(String.format(LOCK_KEY, userId), LOCK_TTL, LOCK_WAIT, () -> {
            User u = userMapper.selectById(userId);
            if (u == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
            }
            modifier.accept(u);
            boolean updated = userMapper.updateById(u) > 0;
            if (!updated) {
                throw new BusinessException(ErrorCode.CONFLICT, "代币处理冲突，请重试");
            }
            return u;
        });
    }
}
