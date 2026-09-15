package com.xjtu.iron.distributed.lock.provider.redisson;

import com.xjtu.iron.distributed.lock.api.LockWaitStrategy;
import com.xjtu.iron.distributed.lock.api.model.LockAutoRenewMode;
import com.xjtu.iron.distributed.lock.api.model.LockOptions;
import com.xjtu.iron.distributed.lock.spi.LockProvider;
import com.xjtu.iron.distributed.lock.spi.LockProviderCapabilities;
import com.xjtu.iron.distributed.lock.spi.protocol.acquire.LockAcquireRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.acquire.LockAcquireResponse;
import com.xjtu.iron.distributed.lock.spi.protocol.check.LockCheckRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.check.LockCheckResponse;
import com.xjtu.iron.distributed.lock.spi.protocol.common.LockLease;
import com.xjtu.iron.distributed.lock.spi.protocol.release.LockReleaseRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.release.LockReleaseResponse;
import com.xjtu.iron.distributed.lock.spi.protocol.renew.LockRenewRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.renew.LockRenewResponse;
import org.redisson.api.RFencedLock;
import org.redisson.api.RFuture;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redisson 的 {@link LockProvider}。
 *
 * <p>这不是第二套 DistributedLockClient。它只负责把 Redisson 的 RLock/RFencedLock 语义适配到
 * iron-lock 已经稳定的 LockProvider SPI 中，业务代码仍只依赖 DistributedLockClient。</p>
 *
 * <p>关键适配原则：</p>
 * <ul>
 *     <li>普通互斥使用 {@link RLock}；native fencing 使用 {@link RFencedLock}；</li>
 *     <li>iron ownerToken 不绑定 Java 线程；Redisson 绑定 owner threadId，因此通过
 *         {@link RedissonOwnershipRegistry} 保存 acquire 时的 threadId，支持跨 Java 线程 release/check；</li>
 *     <li>为了不把 Redisson 的 reentrant 语义意外暴露给 iron-lock，同一 threadId + lockKey 的第二次
 *         acquire 会被本 Provider 当作 NOT_ACQUIRED；</li>
 *     <li>{@link LockWaitStrategy#PROVIDER_NATIVE} 把完整 waitTime 交给 Redisson 原生 Pub/Sub 等待；</li>
 *     <li>autoRenew=true 时不传 leaseTime，使用 Redisson 自己的 watchdog；Core watchdog 只做归属检查
 *         与 maxRenewTime 上限控制，避免“双 watchdog”；</li>
 *     <li>RFencedLock 使用 tryLockAndGetToken(...)，保证“获得锁 + fencing token”作为同一次 Redisson
 *         acquire 的结果返回，避免先 tryLock 再 getToken 产生 token 归属竞态；</li>
 *     <li>不使用 isHeldByThread + expire 两条命令模拟手动 renew，避免 TOCTOU 给新 owner 误续期。</li>
 * </ul>
 */
public final class RedissonLockProvider implements LockProvider {

    private final RedissonClient redissonClient;
    private final RedissonLockKeyBuilder keyBuilder;
    private final RedissonOwnershipRegistry ownershipRegistry;
    private final Duration watchdogTimeout;

    public RedissonLockProvider(RedissonClient redissonClient, RedissonLockKeyBuilder keyBuilder, RedissonOwnershipRegistry ownershipRegistry,
            Duration watchdogTimeout) {
        this.redissonClient = Objects.requireNonNull(redissonClient, "redissonClient must not be null");
        this.keyBuilder = Objects.requireNonNull(keyBuilder, "keyBuilder must not be null");
        this.ownershipRegistry = Objects.requireNonNull(ownershipRegistry, "ownershipRegistry must not be null");
        this.watchdogTimeout = requirePositive(watchdogTimeout, "watchdogTimeout");
    }

    @Override
    public String providerName() {
        return RedissonLockConstants.PROVIDER_NAME;
    }

    @Override
    public void validateOptions(LockOptions options) {
        Objects.requireNonNull(options, "options must not be null");

        /*
         * Redisson watchdog timeout 是 RedissonClient/Config 级别，而 LockOptions.leaseTime 是一次请求级别。
         * 第一版要求二者相等，让 LockHandle.leaseTime 始终能够表达当前 Provider 的 TTL 时间窗，
         * 避免配置 30s、请求却写 10s 这类“API 看起来一种语义，底层实际另一种语义”的情况。
         */
        if (options.isAutoRenew() && !options.getLeaseTime().equals(watchdogTimeout)) {
            throw new IllegalArgumentException(
                    "redisson provider-managed watchdog uses a client-level timeout. "
                            + "When autoRenew=true, LockOptions.leaseTime must equal configured redisson watchdogTimeout. "
                            + "leaseTime=" + options.getLeaseTime() + ", watchdogTimeout=" + watchdogTimeout);
        }
    }

    @Override
    public LockAcquireResponse acquire(LockAcquireRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String lockKey = keyBuilder.buildLockKey(request.getNamespace(), request.getLockName());
        long threadId = Thread.currentThread().getId();

        RedissonOwnershipRegistry.Reservation reservation = reserveOwner(request, lockKey, threadId);
        if (!reservation.isReserved()) {
            return LockAcquireResponse.notAcquired(Duration.ZERO);
        }

        LockOptions options = request.getOptions();
        long waitMillis = options.getWaitStrategy() == LockWaitStrategy.PROVIDER_NATIVE
                ? options.getWaitTime().toMillis()
                : 0L;

        try {
            LockAcquireResponse response = request.isNativeFencingRequired()
                    ? acquireFenced(request, lockKey, waitMillis)
                    : acquirePlain(request, lockKey, waitMillis);
            if (!response.isAcquired()) {
                ownershipRegistry.remove(request.getOwnerToken());
            }
            return response;
        } catch (InterruptedException interrupted) {
            ownershipRegistry.remove(request.getOwnerToken());
            Thread.currentThread().interrupt();
            return LockAcquireResponse.failed(interrupted, "redisson acquire interrupted");
        } catch (Throwable error) {
            ownershipRegistry.remove(request.getOwnerToken());
            return LockAcquireResponse.failed(unwrap(error), "redisson acquire failed");
        }
    }

    /**
     * 预留当前 Java threadId 作为 Redisson owner。
     *
     * <p>若同一线程对同一 lockKey 仍有旧记录，先询问 Redis：旧 owner 若已经因固定 lease 过期，
     * 清理本地陈旧映射后允许新 ownerToken 再次 acquire；若远端仍由该 threadId 持有，则明确返回冲突，
     * 不让 Redisson 的 reentrant 行为穿透 iron-lock API。</p>
     */
    private RedissonOwnershipRegistry.Reservation reserveOwner(LockAcquireRequest request, String lockKey, long threadId) {
        RedissonOwnershipRegistry.Reservation reservation = ownershipRegistry.reserve(request.getOwnerToken(), lockKey, threadId);
        if (reservation.isReserved()) {
            return reservation;
        }

        String conflictOwnerToken = reservation.conflictOwnerToken();
        RLock lock = redissonClient.getLock(lockKey);
        if (!lock.isHeldByThread(threadId)) {
            ownershipRegistry.remove(conflictOwnerToken);
            return ownershipRegistry.reserve(request.getOwnerToken(), lockKey, threadId);
        }
        return reservation;
    }

    private LockAcquireResponse acquirePlain(LockAcquireRequest request, String lockKey, long waitMillis) throws InterruptedException {
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired;
        if (request.getOptions().isAutoRenew()) {
            // 不传 leaseTime 才会启用 Redisson Config.lockWatchdogTimeout。
            acquired = lock.tryLock(waitMillis, TimeUnit.MILLISECONDS);
        } else {
            acquired = lock.tryLock(waitMillis, request.getOptions().getLeaseTime().toMillis(), TimeUnit.MILLISECONDS);
        }
        if (!acquired) {
            return LockAcquireResponse.notAcquired(remainingTtl(lock));
        }
        return LockAcquireResponse.acquired(buildLease(request, lockKey, null));
    }

    private LockAcquireResponse acquireFenced(LockAcquireRequest request, String lockKey, long waitMillis) throws InterruptedException {
        RFencedLock lock = redissonClient.getFencedLock(lockKey);

        /*
         * 一定使用“acquire 并返回 token”的 API，而不是 tryLock() 成功后再 getToken()。
         * 后者中间存在 lease 到期/其他 owner 再获取的时间窗，可能把后来 owner 的 token 错配给本次 lease。
         */
        Long token;
        if (request.getOptions().isAutoRenew()) {
            token = lock.tryLockAndGetToken(waitMillis, TimeUnit.MILLISECONDS);
        } else {
            token = lock.tryLockAndGetToken(waitMillis, request.getOptions().getLeaseTime().toMillis(), TimeUnit.MILLISECONDS);
        }
        if (token == null) {
            return LockAcquireResponse.notAcquired(remainingTtl(lock));
        }
        if (token <= 0L) {
            safeUnlock(lock, Thread.currentThread().getId());
            return LockAcquireResponse.failed(new IllegalStateException("redisson fenced lock returned invalid fencing token: " + token),
                    "redisson native fencing token is invalid");
        }
        return LockAcquireResponse.acquired(buildLease(request, lockKey, token));
    }

    private LockLease buildLease(LockAcquireRequest request, String lockKey, Long fencingToken) {
        Duration actualLeaseTime = request.getOptions().isAutoRenew()
                ? watchdogTimeout
                : request.getOptions().getLeaseTime();
        Instant acquiredAt = Instant.now();
        return LockLease.builder()
                .providerName(providerName())
                .namespace(request.getNamespace())
                .lockName(request.getLockName())
                .lockKey(lockKey)
                .ownerToken(request.getOwnerToken())
                .fencingToken(fencingToken)
                .fencingTokenProviderName(fencingToken == null ? null : providerName())
                .leaseTime(actualLeaseTime)
                .acquiredAt(acquiredAt)
                .expireAt(acquiredAt.plus(actualLeaseTime))
                .build();
    }

    @Override
    public LockReleaseResponse release(LockReleaseRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        RedissonOwnershipRegistry.Ownership ownership = ownershipRegistry.find(request.getOwnerToken());
        if (ownership == null || !ownership.lockKey().equals(request.getLockKey())) {
            return diagnoseMissingOwner(redissonClient.getLock(request.getLockKey()));
        }

        RLock lock = redissonClient.getLock(request.getLockKey());
        try {
            if (!lock.isHeldByThread(ownership.threadId())) {
                LockReleaseResponse response = lock.isLocked()
                        ? LockReleaseResponse.notOwner()
                        : LockReleaseResponse.notFound();
                ownershipRegistry.remove(request.getOwnerToken());
                return response;
            }

            // 即使 release 发生在另一个 Java 线程，也显式携带 acquire 时保存的 threadId。
            await(lock.unlockAsync(ownership.threadId()));
            ownershipRegistry.remove(request.getOwnerToken());
            return LockReleaseResponse.released();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return LockReleaseResponse.failed(interrupted, "redisson release interrupted");
        } catch (Throwable error) {
            Throwable cause = unwrap(error);
            if (cause instanceof IllegalMonitorStateException) {
                LockReleaseResponse response = diagnoseMissingOwner(lock);
                ownershipRegistry.remove(request.getOwnerToken());
                return response;
            }
            return LockReleaseResponse.failed(cause, "redisson release failed");
        }
    }

    @Override
    public LockRenewResponse renew(LockRenewRequest request) {
        /*
         * 不用 isHeldByThread(threadId) + expire(...) 模拟 renew：两个命令之间锁可能过期并被新 owner 获得，
         * 随后的 expire 会误给新 owner 续命。Redisson Provider 的 autoRenew 明确走 PROVIDER_MANAGED watchdog。
         */
        return LockRenewResponse.failed(
                new UnsupportedOperationException("manual renew is not supported by redisson provider; use autoRenew=true"),
                "redisson manual renew is unsupported");
    }

    @Override
    public LockCheckResponse check(LockCheckRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        RedissonOwnershipRegistry.Ownership ownership = ownershipRegistry.find(request.getOwnerToken());
        RLock lock = redissonClient.getLock(request.getLockKey());
        if (ownership == null || !ownership.lockKey().equals(request.getLockKey())) {
            return diagnoseMissingOwnerForCheck(lock);
        }

        try {
            if (lock.isHeldByThread(ownership.threadId())) {
                return LockCheckResponse.held();
            }
            LockCheckResponse response = lock.isLocked()
                    ? LockCheckResponse.notOwner()
                    : LockCheckResponse.notFound();
            ownershipRegistry.remove(request.getOwnerToken());
            return response;
        } catch (Throwable error) {
            return LockCheckResponse.failed(unwrap(error), "redisson check failed");
        }
    }

    @Override
    public LockProviderCapabilities capabilities() {
        return LockProviderCapabilities.builder()
                .autoRenewMode(LockAutoRenewMode.PROVIDER_MANAGED)
                .manualRenewSupported(false)
                .fencingTokenSupported(true)
                .nativeWaitSupported(true)
                // Redisson 底层有 fair/reentrant，但 iron-lock 公共 API 尚未正式定义这两个语义。
                .fairLockSupported(false)
                .reentrantSupported(false)
                .build();
    }

    private LockReleaseResponse diagnoseMissingOwner(RLock lock) {
        try {
            return lock.isLocked() ? LockReleaseResponse.notOwner() : LockReleaseResponse.notFound();
        } catch (Throwable error) {
            return LockReleaseResponse.failed(unwrap(error), "redisson release ownership check failed");
        }
    }

    private LockCheckResponse diagnoseMissingOwnerForCheck(RLock lock) {
        try {
            return lock.isLocked() ? LockCheckResponse.notOwner() : LockCheckResponse.notFound();
        } catch (Throwable error) {
            return LockCheckResponse.failed(unwrap(error), "redisson check ownership check failed");
        }
    }

    private static Duration remainingTtl(RLock lock) {
        try {
            long ttl = lock.remainTimeToLive();
            return ttl > 0L ? Duration.ofMillis(ttl) : Duration.ZERO;
        } catch (Throwable ignored) {
            return Duration.ZERO;
        }
    }

    private static void safeUnlock(RLock lock, long threadId) {
        try {
            if (lock.isHeldByThread(threadId)) {
                await(lock.unlockAsync(threadId));
            }
        } catch (Throwable ignored) {
            // 失败路径 best-effort 清理，不能覆盖真正的 fencing/acquire 主错误。
        }
    }

    private static <T> T await(RFuture<T> future) throws Exception {
        try {
            return future.get();
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw error;
        }
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof ExecutionException && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
