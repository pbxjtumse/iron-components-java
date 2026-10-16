package com.xjtu.iron.distributed.lock.spi.protocol.release;


import com.xjtu.iron.distributed.lock.spi.protocol.common.LockLease;
import com.xjtu.iron.distributed.lock.spi.protocol.common.LockLeaseRef;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Provider 解锁请求。 */
public final class LockReleaseRequest {

    private final LockLeaseRef leaseRef;
    private final String releaseReason;
    private final boolean publishReleaseEvent;
    private final Map<String, String> attributes;

    private LockReleaseRequest(Builder builder) {
        this.leaseRef = Objects.requireNonNull(builder.leaseRef, "leaseRef must not be null");
        this.releaseReason = builder.releaseReason;
        this.publishReleaseEvent = builder.publishReleaseEvent;
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(builder.attributes));
    }

    public static Builder builder() { return new Builder(); }

    public static LockReleaseRequest fromLease(LockLease lease) {
        return builder().leaseRef(LockLeaseRef.fromLease(lease)).build();
    }

    public LockLeaseRef getLeaseRef() { return leaseRef; }
    public String getNamespace() { return leaseRef.getNamespace(); }
    public String getLockName() { return leaseRef.getLockName(); }
    public String getLockKey() { return leaseRef.getLockKey(); }
    public String getOwnerToken() { return leaseRef.getOwnerToken(); }
    public String getReleaseReason() { return releaseReason; }
    public boolean isPublishReleaseEvent() { return publishReleaseEvent; }
    public Map<String, String> getAttributes() { return attributes; }

    public static final class Builder {
        private LockLeaseRef leaseRef;
        private LockLeaseRef.Builder leaseRefBuilder;
        private String releaseReason = "normal";
        private boolean publishReleaseEvent = true;
        private Map<String, String> attributes = new LinkedHashMap<>();
        private Builder() {}
        public Builder leaseRef(LockLeaseRef leaseRef) { this.leaseRef = leaseRef; return this; }
        public Builder namespace(String namespace) { ensureLeaseRefBuilder().namespace(namespace); return this; }
        public Builder lockName(String lockName) { ensureLeaseRefBuilder().lockName(lockName); return this; }
        public Builder lockKey(String lockKey) { ensureLeaseRefBuilder().lockKey(lockKey); return this; }
        public Builder ownerToken(String ownerToken) { ensureLeaseRefBuilder().ownerToken(ownerToken); return this; }
        public Builder releaseReason(String releaseReason) { this.releaseReason = releaseReason; return this; }
        public Builder publishReleaseEvent(boolean publishReleaseEvent) { this.publishReleaseEvent = publishReleaseEvent; return this; }
        public Builder attributes(Map<String, String> attributes) { this.attributes = attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes); return this; }
        public LockReleaseRequest build() { if (leaseRef == null && leaseRefBuilder != null) { leaseRef = leaseRefBuilder.build(); } return new LockReleaseRequest(this); }
        private LockLeaseRef.Builder ensureLeaseRefBuilder() { if (leaseRefBuilder == null) { leaseRefBuilder = LockLeaseRef.builder(); } this.leaseRef = null; return leaseRefBuilder; }
    }
}
