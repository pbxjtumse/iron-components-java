package com.xjtu.iron.distributed.lock.spi.protocol.acquire;

import com.xjtu.iron.distributed.lock.spi.LockProviderError;
import com.xjtu.iron.distributed.lock.spi.protocol.common.LockLease;
import com.xjtu.iron.distributed.lock.spi.protocol.common.LockProviderResponse;

import java.time.Duration;
import java.util.Objects;

/** Provider 加锁响应。 */
public final class LockAcquireResponse implements LockProviderResponse<LockAcquireStatus> {

    private final LockAcquireStatus status;
    private final LockLease lease;
    private final Duration remainingTtl;
    private final LockProviderError lockProviderError;

    private LockAcquireResponse(Builder builder) {
        this.status = Objects.requireNonNull(builder.status, "status must not be null");
        this.lease = builder.lease;
        this.remainingTtl = builder.remainingTtl;
        this.lockProviderError = builder.lockProviderError == null ? LockProviderError.none() : builder.lockProviderError;
        if (status == LockAcquireStatus.ACQUIRED && lease == null) {
            throw new IllegalArgumentException("lease must not be null when status is ACQUIRED");
        }
        if (status != LockAcquireStatus.ACQUIRED && lease != null) {
            throw new IllegalArgumentException("lease must be null when status is not ACQUIRED");
        }
    }

    public static Builder builder() { return new Builder(); }

    public static LockAcquireResponse acquired(LockLease lease) {
        return builder().status(LockAcquireStatus.ACQUIRED).lease(lease).build();
    }

    public static LockAcquireResponse notAcquired(Duration remainingTtl) {
        return builder().status(LockAcquireStatus.NOT_ACQUIRED).remainingTtl(remainingTtl).build();
    }

    public static LockAcquireResponse failed(Throwable error) {
        return builder().status(LockAcquireStatus.PROVIDER_ERROR).providerError(LockProviderError.of(error)).build();
    }

    public static LockAcquireResponse failed(Throwable error, String message) {
        return builder().status(LockAcquireStatus.PROVIDER_ERROR).providerError(LockProviderError.of(error, message)).build();
    }

    @Override
    public LockAcquireStatus getStatus() { return status; }

    @Override
    public LockProviderError getProviderError() { return lockProviderError; }

    public boolean isAcquired() { return status == LockAcquireStatus.ACQUIRED; }
    public boolean isNotAcquired() { return status == LockAcquireStatus.NOT_ACQUIRED; }
    public LockLease getLease() { return lease; }
    public Duration getRemainingTtl() { return remainingTtl; }
    public boolean hasError() { return hasProviderError(); }

    public static final class Builder {
        private LockAcquireStatus status;
        private LockLease lease;
        private Duration remainingTtl;
        private LockProviderError lockProviderError;
        private Builder() {}
        public Builder status(LockAcquireStatus status) { this.status = status; return this; }
        public Builder lease(LockLease lease) { this.lease = lease; return this; }
        public Builder remainingTtl(Duration remainingTtl) { this.remainingTtl = remainingTtl; return this; }
        public Builder providerError(LockProviderError lockProviderError) { this.lockProviderError = lockProviderError; return this; }
        public Builder error(Throwable error) { this.lockProviderError = LockProviderError.of(error); return this; }
        public Builder message(String message) { this.lockProviderError = LockProviderError.of(lockProviderError == null ? null : lockProviderError.getCause(), message); return this; }
        public LockAcquireResponse build() { return new LockAcquireResponse(this); }
    }
}
