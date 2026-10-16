package com.xjtu.iron.distributed.lock.spi.protocol.release;

import com.xjtu.iron.distributed.lock.spi.LockProviderError;
import com.xjtu.iron.distributed.lock.spi.protocol.common.LockProviderResponse;

import java.util.Objects;

/** Provider 解锁响应。 */
public final class LockReleaseResponse implements LockProviderResponse<LockReleaseStatus> {

    private final LockReleaseStatus status;
    private final LockProviderError lockProviderError;

    private LockReleaseResponse(Builder builder) {
        this.status = Objects.requireNonNull(builder.status, "status must not be null");
        this.lockProviderError = builder.lockProviderError == null ? LockProviderError.none() : builder.lockProviderError;
    }

    public static Builder builder() { return new Builder(); }
    public static LockReleaseResponse released() { return builder().status(LockReleaseStatus.RELEASED).build(); }
    public static LockReleaseResponse notOwner() { return builder().status(LockReleaseStatus.NOT_OWNER).build(); }
    public static LockReleaseResponse notFound() { return builder().status(LockReleaseStatus.NOT_FOUND).build(); }
    public static LockReleaseResponse failed(Throwable error) { return builder().status(LockReleaseStatus.PROVIDER_ERROR).providerError(LockProviderError.of(error)).build(); }
    public static LockReleaseResponse failed(Throwable error, String message) { return builder().status(LockReleaseStatus.PROVIDER_ERROR).providerError(LockProviderError.of(error, message)).build(); }

    @Override
    public LockReleaseStatus getStatus() { return status; }
    @Override
    public LockProviderError getProviderError() { return lockProviderError; }
    public boolean isReleased() { return status == LockReleaseStatus.RELEASED; }
    public boolean isLockLost() { return status == LockReleaseStatus.NOT_OWNER || status == LockReleaseStatus.NOT_FOUND; }

    public static final class Builder {
        private LockReleaseStatus status;
        private LockProviderError lockProviderError;
        private Builder() {}
        public Builder status(LockReleaseStatus status) { this.status = status; return this; }
        public Builder providerError(LockProviderError lockProviderError) { this.lockProviderError = lockProviderError; return this; }
        public Builder error(Throwable error) { this.lockProviderError = LockProviderError.of(error); return this; }
        public Builder message(String message) { this.lockProviderError = LockProviderError.of(lockProviderError == null ? null : lockProviderError.getCause(), message); return this; }
        public LockReleaseResponse build() { return new LockReleaseResponse(this); }
    }
}
