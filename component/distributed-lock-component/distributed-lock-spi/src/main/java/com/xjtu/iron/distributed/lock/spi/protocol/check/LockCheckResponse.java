package com.xjtu.iron.distributed.lock.spi.protocol.check;

import com.xjtu.iron.distributed.lock.spi.LockProviderError;
import com.xjtu.iron.distributed.lock.spi.protocol.common.LockProviderResponse;

import java.util.Objects;

/** Provider 持锁检查响应。 */
public final class LockCheckResponse implements LockProviderResponse<LockCheckStatus> {

    private final LockCheckStatus status;
    private final LockProviderError lockProviderError;

    private LockCheckResponse(Builder builder) {
        this.status = Objects.requireNonNull(builder.status, "status must not be null");
        this.lockProviderError = builder.lockProviderError == null ? LockProviderError.none() : builder.lockProviderError;
    }

    public static Builder builder() { return new Builder(); }
    public static LockCheckResponse held() { return builder().status(LockCheckStatus.HELD).build(); }
    public static LockCheckResponse notOwner() { return builder().status(LockCheckStatus.NOT_OWNER).build(); }
    public static LockCheckResponse notFound() { return builder().status(LockCheckStatus.NOT_FOUND).build(); }
    public static LockCheckResponse failed(Throwable error) { return builder().status(LockCheckStatus.PROVIDER_ERROR).providerError(LockProviderError.of(error)).build(); }
    public static LockCheckResponse failed(Throwable error, String message) { return builder().status(LockCheckStatus.PROVIDER_ERROR).providerError(LockProviderError.of(error, message)).build(); }

    @Override
    public LockCheckStatus getStatus() { return status; }
    @Override
    public LockProviderError getProviderError() { return lockProviderError; }
    public boolean isHeld() { return status == LockCheckStatus.HELD; }
    public boolean isLockLost() { return status == LockCheckStatus.NOT_OWNER || status == LockCheckStatus.NOT_FOUND; }

    public static final class Builder {
        private LockCheckStatus status;
        private LockProviderError lockProviderError;
        private Builder() {}
        public Builder status(LockCheckStatus status) { this.status = status; return this; }
        public Builder providerError(LockProviderError lockProviderError) { this.lockProviderError = lockProviderError; return this; }
        public Builder error(Throwable error) { this.lockProviderError = LockProviderError.of(error); return this; }
        public Builder message(String message) { this.lockProviderError = LockProviderError.of(lockProviderError == null ? null : lockProviderError.getCause(), message); return this; }
        public LockCheckResponse build() { return new LockCheckResponse(this); }
    }
}
