package com.xjtu.iron.distributed.lock.spi.protocol.check;


import com.xjtu.iron.distributed.lock.spi.protocol.common.LockLease;
import com.xjtu.iron.distributed.lock.spi.protocol.common.LockLeaseRef;

import java.util.Objects;

/** Provider 持锁检查请求。 */
public final class LockCheckRequest {

    private final LockLeaseRef leaseRef;

    private LockCheckRequest(Builder builder) {
        this.leaseRef = Objects.requireNonNull(builder.leaseRef, "leaseRef must not be null");
    }

    public static Builder builder() { return new Builder(); }

    public static LockCheckRequest fromLease(LockLease lease) {
        return builder().leaseRef(LockLeaseRef.fromLease(lease)).build();
    }

    public LockLeaseRef getLeaseRef() { return leaseRef; }
    public String getNamespace() { return leaseRef.getNamespace(); }
    public String getLockName() { return leaseRef.getLockName(); }
    public String getLockKey() { return leaseRef.getLockKey(); }
    public String getOwnerToken() { return leaseRef.getOwnerToken(); }

    public static final class Builder {
        private LockLeaseRef leaseRef;
        private LockLeaseRef.Builder leaseRefBuilder;
        private Builder() {}
        public Builder leaseRef(LockLeaseRef leaseRef) { this.leaseRef = leaseRef; return this; }
        public Builder namespace(String namespace) { ensureLeaseRefBuilder().namespace(namespace); return this; }
        public Builder lockName(String lockName) { ensureLeaseRefBuilder().lockName(lockName); return this; }
        public Builder lockKey(String lockKey) { ensureLeaseRefBuilder().lockKey(lockKey); return this; }
        public Builder ownerToken(String ownerToken) { ensureLeaseRefBuilder().ownerToken(ownerToken); return this; }
        public LockCheckRequest build() { if (leaseRef == null && leaseRefBuilder != null) { leaseRef = leaseRefBuilder.build(); } return new LockCheckRequest(this); }
        private LockLeaseRef.Builder ensureLeaseRefBuilder() { if (leaseRefBuilder == null) { leaseRefBuilder = LockLeaseRef.builder(); } this.leaseRef = null; return leaseRefBuilder; }
    }
}
