package com.xjtu.iron.distributed.lock.core.acquire;

import com.xjtu.iron.distributed.lock.api.model.LockOptions;
import com.xjtu.iron.distributed.lock.core.fencing.coordinator.FencingTokenPlan;
import com.xjtu.iron.distributed.lock.spi.LockProvider;
import com.xjtu.iron.distributed.lock.spi.protocol.acquire.LockAcquireRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.acquire.LockAcquireResponse;

import java.time.Duration;
import java.util.Objects;

/**
 * 一次 LockProvider acquire 响应的解释上下文。
 *
 * <p>该对象把状态处理器所需的数据一次性封装起来，避免处理器继续反向依赖
 * {@code DefaultDistributedLockClient}。</p>
 */
public final class LockAcquireOutcomeContext {

    private final String lockName;
    private final LockProvider provider;
    private final LockOptions options;
    private final LockAcquireRequest request;
    private final LockAcquireResponse response;
    private final FencingTokenPlan fencingPlan;
    private final Duration waitDuration;

    private LockAcquireOutcomeContext(Builder builder) {
        this.lockName = Objects.requireNonNull(builder.lockName, "lockName must not be null");
        this.provider = Objects.requireNonNull(builder.provider, "provider must not be null");
        this.options = Objects.requireNonNull(builder.options, "options must not be null");
        this.request = Objects.requireNonNull(builder.request, "request must not be null");
        this.response = Objects.requireNonNull(builder.response, "response must not be null");
        this.fencingPlan = Objects.requireNonNull(builder.fencingPlan, "fencingPlan must not be null");
        this.waitDuration = builder.waitDuration == null ? Duration.ZERO : builder.waitDuration;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String lockName() { return lockName; }
    public LockProvider provider() { return provider; }
    public LockOptions options() { return options; }
    public LockAcquireRequest request() { return request; }
    public LockAcquireResponse response() { return response; }
    public FencingTokenPlan fencingPlan() { return fencingPlan; }
    public Duration waitDuration() { return waitDuration; }

    public static final class Builder {
        private String lockName;
        private LockProvider provider;
        private LockOptions options;
        private LockAcquireRequest request;
        private LockAcquireResponse response;
        private FencingTokenPlan fencingPlan;
        private Duration waitDuration;

        private Builder() {}

        public Builder lockName(String lockName) { this.lockName = lockName; return this; }
        public Builder provider(LockProvider provider) { this.provider = provider; return this; }
        public Builder options(LockOptions options) { this.options = options; return this; }
        public Builder request(LockAcquireRequest request) { this.request = request; return this; }
        public Builder response(LockAcquireResponse response) { this.response = response; return this; }
        public Builder fencingPlan(FencingTokenPlan fencingPlan) { this.fencingPlan = fencingPlan; return this; }
        public Builder waitDuration(Duration waitDuration) { this.waitDuration = waitDuration; return this; }

        public LockAcquireOutcomeContext build() {
            return new LockAcquireOutcomeContext(this);
        }
    }
}
