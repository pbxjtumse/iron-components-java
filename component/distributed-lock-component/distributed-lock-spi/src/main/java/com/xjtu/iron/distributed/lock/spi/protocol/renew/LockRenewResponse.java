package com.xjtu.iron.distributed.lock.spi.protocol.renew;

import com.xjtu.iron.distributed.lock.spi.LockProviderError;
import com.xjtu.iron.distributed.lock.spi.protocol.common.LockProviderResponse;

import java.time.Instant;
import java.util.Objects;

/** Provider 续期响应。 */
public final class LockRenewResponse implements LockProviderResponse<LockRenewStatus> {

    private final LockRenewStatus status;
    private final Instant newExpireAt;
    private final LockProviderError lockProviderError;

    private LockRenewResponse(Builder builder) {
        this.status = Objects.requireNonNull(builder.status, "status must not be null");
        this.newExpireAt = builder.newExpireAt;
        this.lockProviderError = builder.lockProviderError == null ? LockProviderError.none() : builder.lockProviderError;
    }

    public static Builder builder() { return new Builder(); }
    public static LockRenewResponse renewed(Instant newExpireAt) { return builder().status(LockRenewStatus.RENEWED).newExpireAt(newExpireAt).build(); }
    public static LockRenewResponse notOwner() { return builder().status(LockRenewStatus.NOT_OWNER).build(); }
    public static LockRenewResponse notFound() { return builder().status(LockRenewStatus.NOT_FOUND).build(); }
    public static LockRenewResponse failed(Throwable error) { return builder().status(LockRenewStatus.PROVIDER_ERROR).providerError(LockProviderError.of(error)).build(); }
    public static LockRenewResponse failed(Throwable error, String message) { return builder().status(LockRenewStatus.PROVIDER_ERROR).providerError(LockProviderError.of(error, message)).build(); }

    @Override
    public LockRenewStatus getStatus() { return status; }
    @Override
    public LockProviderError getProviderError() { return lockProviderError; }
    public Instant getNewExpireAt() { return newExpireAt; }
    public boolean isRenewed() { return status == LockRenewStatus.RENEWED; }
    public boolean isLockLost() { return status == LockRenewStatus.NOT_OWNER || status == LockRenewStatus.NOT_FOUND; }

    public static final class Builder {
        private LockRenewStatus status;
        private Instant newExpireAt;
        private LockProviderError lockProviderError;
        private Builder() {}
        public Builder status(LockRenewStatus status) { this.status = status; return this; }
        public Builder newExpireAt(Instant newExpireAt) { this.newExpireAt = newExpireAt; return this; }
        public Builder providerError(LockProviderError lockProviderError) { this.lockProviderError = lockProviderError; return this; }
        public Builder error(Throwable error) { this.lockProviderError = LockProviderError.of(error); return this; }
        public Builder message(String message) { this.lockProviderError = LockProviderError.of(lockProviderError == null ? null : lockProviderError.getCause(), message); return this; }
        public LockRenewResponse build() { return new LockRenewResponse(this); }
    }
}
