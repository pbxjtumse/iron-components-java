package com.xjtu.iron.distributed.lock.provider.redis;

import com.xjtu.iron.distributed.lock.api.model.LockAutoRenewMode;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Redis 分布式锁 Provider。 */
public final class RedisLockProvider implements LockProvider {

    private final RedisLockScriptExecutor scriptExecutor;
    private final RedisLockKeyBuilder keyBuilder;
    private final RedisScriptResultParser resultParser;

    public RedisLockProvider(RedisLockScriptExecutor scriptExecutor) {
        this(scriptExecutor, new RedisLockKeyBuilder(), new RedisScriptResultParser());
    }

    public RedisLockProvider(RedisLockScriptExecutor scriptExecutor, RedisLockKeyBuilder keyBuilder) {
        this(scriptExecutor, keyBuilder, new RedisScriptResultParser());
    }

    public RedisLockProvider(RedisLockScriptExecutor scriptExecutor, RedisLockKeyBuilder keyBuilder, RedisScriptResultParser resultParser) {
        this.scriptExecutor = Objects.requireNonNull(scriptExecutor, "scriptExecutor must not be null");
        this.keyBuilder = Objects.requireNonNull(keyBuilder, "keyBuilder must not be null");
        this.resultParser = Objects.requireNonNull(resultParser, "resultParser must not be null");
    }

    @Override
    public String providerName() { return RedisLockConstants.PROVIDER_NAME; }

    @Override
    public LockAcquireResponse acquire(LockAcquireRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String lockKey = keyBuilder.buildLockKey(request.getNamespace(), request.getLockName());
        String fencingKey = keyBuilder.buildFencingKey(request.getNamespace(), request.getLockName());
        List<String> keys = Arrays.asList(lockKey, fencingKey);
        List<String> args = Arrays.asList(request.getOwnerToken(), String.valueOf(request.getOptions().getLeaseTime().toMillis()),
                request.isNativeFencingRequired() ? "1" : "0");
        try {
            RedisScriptResultParser.AcquireResult result = resultParser.parseAcquire(scriptExecutor.execute(RedisLockScripts.ACQUIRE, keys, args));
            if (result.getFlag() == 1L) {
                Instant acquiredAt = Instant.now();
                LockLease lease = LockLease.builder()
                        .providerName(providerName())
                        .namespace(request.getNamespace())
                        .lockName(request.getLockName())
                        .lockKey(lockKey)
                        .ownerToken(request.getOwnerToken())
                        .fencingToken(result.getFencingToken())
                        .fencingTokenProviderName(result.getFencingToken() == null ? null : providerName())
                        .leaseTime(request.getOptions().getLeaseTime())
                        .acquiredAt(acquiredAt)
                        .expireAt(acquiredAt.plus(request.getOptions().getLeaseTime()))
                        .build();
                return LockAcquireResponse.acquired(lease);
            }
            Duration ttl = Duration.ofMillis(Math.max(0L, result.getRemainingTtlMillis()));
            return LockAcquireResponse.notAcquired(ttl);
        } catch (Throwable e) {
            return LockAcquireResponse.failed(e);
        }
    }

    @Override
    public LockReleaseResponse release(LockReleaseRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        try {
            long result = resultParser.parseLong(scriptExecutor.execute(RedisLockScripts.RELEASE,
                    Arrays.asList(request.getLockKey()), Arrays.asList(request.getOwnerToken())));
            if (result == 1L) { return LockReleaseResponse.released(); }
            if (result == -1L) { return LockReleaseResponse.notFound(); }
            return LockReleaseResponse.notOwner();
        } catch (Throwable e) {
            return LockReleaseResponse.failed(e);
        }
    }

    @Override
    public LockRenewResponse renew(LockRenewRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        try {
            long result = resultParser.parseLong(scriptExecutor.execute(RedisLockScripts.RENEW,
                    Arrays.asList(request.getLockKey()),
                    Arrays.asList(request.getOwnerToken(), String.valueOf(request.getLeaseTime().toMillis()))));
            if (result == 1L) { return LockRenewResponse.renewed(Instant.now().plus(request.getLeaseTime())); }
            if (result == -1L) { return LockRenewResponse.notFound(); }
            return LockRenewResponse.notOwner();
        } catch (Throwable e) {
            return LockRenewResponse.failed(e);
        }
    }

    @Override
    public LockCheckResponse check(LockCheckRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        try {
            long result = resultParser.parseLong(scriptExecutor.execute(RedisLockScripts.CHECK,
                    Arrays.asList(request.getLockKey()), Arrays.asList(request.getOwnerToken())));
            if (result == 1L) { return LockCheckResponse.held(); }
            if (result == -1L) { return LockCheckResponse.notFound(); }
            return LockCheckResponse.notOwner();
        } catch (Throwable e) {
            return LockCheckResponse.failed(e);
        }
    }

    @Override
    public LockProviderCapabilities capabilities() {
        return LockProviderCapabilities.builder()
                .autoRenewMode(LockAutoRenewMode.CORE_MANAGED)
                .manualRenewSupported(true)
                .fencingTokenSupported(true)
                .nativeWaitSupported(false)
                .fairLockSupported(false)
                .reentrantSupported(false)
                .build();
    }
}
