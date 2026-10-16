package com.xjtu.iron.distributed.lock.spi.protocol.renew;


import com.xjtu.iron.distributed.lock.spi.protocol.common.LockLease;
import com.xjtu.iron.distributed.lock.spi.protocol.common.LockLeaseRef;

import java.time.Duration;
import java.util.Objects;

/** Provider 续期请求。 */
public final class LockRenewRequest {

    private final LockLeaseRef leaseRef;
    private final Duration leaseTime;
    private final String renewReason;

    private LockRenewRequest(Builder builder) {
        this.leaseRef = Objects.requireNonNull(builder.leaseRef, "leaseRef must not be null");
        this.leaseTime = requirePositive(builder.leaseTime, "leaseTime");
        this.renewReason = builder.renewReason;
    }

    public static Builder builder() { return new Builder(); }

    public static LockRenewRequest fromLease(LockLease lease) {
        return builder().leaseRef(LockLeaseRef.fromLease(lease)).leaseTime(lease.getLeaseTime()).build();
    }

    public LockLeaseRef getLeaseRef() { return leaseRef; }
    public String getNamespace() { return leaseRef.getNamespace(); }
    public String getLockName() { return leaseRef.getLockName(); }
    public String getLockKey() { return leaseRef.getLockKey(); }
    public String getOwnerToken() { return leaseRef.getOwnerToken(); }
    public Duration getLeaseTime() { return leaseTime; }
    public String getRenewReason() { return renewReason; }

    private static Duration requirePositive(Duration value, String fieldName) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    public static final class Builder {
        private LockLeaseRef leaseRef;
        private Duration leaseTime;
        private String renewReason = "watchdog";
        private Builder() {}
        public Builder leaseRef(LockLeaseRef leaseRef) { this.leaseRef = leaseRef; return this; }
        public Builder namespace(String namespace) { ensureLeaseRefBuilder().namespace(namespace); return this; }
        public Builder lockName(String lockName) { ensureLeaseRefBuilder().lockName(lockName); return this; }
        public Builder lockKey(String lockKey) { ensureLeaseRefBuilder().lockKey(lockKey); return this; }
        public Builder ownerToken(String ownerToken) { ensureLeaseRefBuilder().ownerToken(ownerToken); return this; }
        public Builder leaseTime(Duration leaseTime) { this.leaseTime = leaseTime; return this; }
        public Builder renewReason(String renewReason) { this.renewReason = renewReason; return this; }
        public LockRenewRequest build() { prepareLeaseRef(); return new LockRenewRequest(this); }
        private LockLeaseRef.Builder leaseRefBuilder;
        private LockLeaseRef.Builder ensureLeaseRefBuilder() { if (leaseRefBuilder == null) { leaseRefBuilder = LockLeaseRef.builder(); } this.leaseRef = null; return leaseRefBuilder; }
        private void prepareLeaseRef() { if (leaseRef == null && leaseRefBuilder != null) { leaseRef = leaseRefBuilder.build(); } }
    }
}
