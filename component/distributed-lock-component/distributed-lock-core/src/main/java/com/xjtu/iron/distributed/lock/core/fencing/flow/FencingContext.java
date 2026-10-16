package com.xjtu.iron.distributed.lock.core.fencing.flow;

import com.xjtu.iron.distributed.lock.api.model.LockOptions;
import com.xjtu.iron.distributed.lock.core.fencing.coordinator.FencingTokenPlan;
import com.xjtu.iron.distributed.lock.spi.LockProvider;
import com.xjtu.iron.distributed.lock.spi.protocol.common.LockLease;

import java.time.Duration;
import java.util.Objects;

/** 一次 fencing flow 执行上下文。 */
public final class FencingContext {

    private final LockProvider lockProvider;
    private final LockOptions options;
    private final FencingTokenPlan plan;
    private final LockLease lease;
    private final Duration waitDuration;

    private FencingContext(Builder builder) {
        this.lockProvider = Objects.requireNonNull(builder.lockProvider, "lockProvider must not be null");
        this.options = Objects.requireNonNull(builder.options, "options must not be null");
        this.plan = Objects.requireNonNull(builder.plan, "plan must not be null");
        this.lease = Objects.requireNonNull(builder.lease, "lease must not be null");
        this.waitDuration = builder.waitDuration == null ? Duration.ZERO : builder.waitDuration;
    }

    public static Builder builder() { return new Builder(); }

    public LockProvider lockProvider() { return lockProvider; }
    public LockOptions options() { return options; }
    public FencingTokenPlan plan() { return plan; }
    public LockLease lease() { return lease; }
    public Duration waitDuration() { return waitDuration; }

    public static final class Builder {
        private LockProvider lockProvider;
        private LockOptions options;
        private FencingTokenPlan plan;
        private LockLease lease;
        private Duration waitDuration;

        private Builder() {}

        public Builder lockProvider(LockProvider lockProvider) { this.lockProvider = lockProvider; return this; }
        public Builder options(LockOptions options) { this.options = options; return this; }
        public Builder plan(FencingTokenPlan plan) { this.plan = plan; return this; }
        public Builder lease(LockLease lease) { this.lease = lease; return this; }
        public Builder waitDuration(Duration waitDuration) { this.waitDuration = waitDuration; return this; }
        public FencingContext build() { return new FencingContext(this); }
    }
}
