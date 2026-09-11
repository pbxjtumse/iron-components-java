package com.xjtu.iron.distributed.lock.spi;

import com.xjtu.iron.distributed.lock.api.model.LockOptions;
import com.xjtu.iron.distributed.lock.spi.protocol.acquire.LockAcquireRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.acquire.LockAcquireResponse;
import com.xjtu.iron.distributed.lock.spi.protocol.check.LockCheckRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.check.LockCheckResponse;
import com.xjtu.iron.distributed.lock.spi.protocol.release.LockReleaseRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.release.LockReleaseResponse;
import com.xjtu.iron.distributed.lock.spi.protocol.renew.LockRenewRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.renew.LockRenewResponse;

/**
 * 分布式锁底层 Provider SPI。
 *
 * <p>Provider 只负责底层存储的一次性原子操作，例如 Redis 的 Lua acquire/release/renew/check，ZK 的节点创建和
 * 删除，Etcd 的事务写入和 lease 续期。等待策略、watchdog、事件、指标、执行模板不应该塞进 Provider。</p>
 */
public interface LockProvider {

    /**
     * Provider 名称。
     *
     * @return Provider 名称，例如 {@code redis}、{@code redisson}、{@code zookeeper}。
     */
    String providerName();

    /**
     * 尝试获取锁。
     *
     * @param request 加锁请求。
     * @return 加锁响应。
     */
    LockAcquireResponse acquire(LockAcquireRequest request);

    /**
     * 释放锁。
     *
     * <p>释放必须校验 ownerToken，不能直接删除底层锁记录。</p>
     *
     * @param request 解锁请求。
     * @return 解锁响应。
     */
    LockReleaseResponse release(LockReleaseRequest request);

    /**
     * 续期锁。
     *
     * <p>续期必须校验 ownerToken，不能给其他 owner 的锁续期。</p>
     *
     * @param request 续期请求。
     * @return 续期响应。
     */
    LockRenewResponse renew(LockRenewRequest request);

    /**
     * 检查当前 ownerToken 是否仍然持有锁。
     *
     * @param request 检查请求。
     * @return 检查响应。
     */
    LockCheckResponse check(LockCheckRequest request);

    /**
     * Provider 能力描述。
     *
     * @return Provider 能力。
     */
    LockProviderCapabilities capabilities();

    /**
     * Provider 对本次 LockOptions 做额外校验。
     *
     * <p>Core 只校验通用语义；Provider 可以在这里校验自己的约束。例如 Redisson 的 watchdog timeout
     * 是 RedissonClient 级配置，当 autoRenew=true 时，本次 leaseTime 必须与该 timeout 一致。</p>
     *
     * @param options 已完成通用校验的锁选项
     * @throws IllegalArgumentException Provider 不接受当前配置时抛出
     */
    default void validateOptions(LockOptions options) {
        // 默认无 Provider 特有约束。
    }
}
