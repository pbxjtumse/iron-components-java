package com.xjtu.iron.distributed.lock.core.spi.protocol;

import com.xjtu.iron.distributed.lock.spi.protocol.check.LockCheckRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.common.LockLease;
import com.xjtu.iron.distributed.lock.spi.protocol.common.LockLeaseRef;
import com.xjtu.iron.distributed.lock.spi.protocol.release.LockReleaseRequest;
import com.xjtu.iron.distributed.lock.spi.protocol.renew.LockRenewRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LockLeaseRefRequestTest {
    @Test
    void renewReleaseCheckShouldShareLeaseRef() {
        LockLease lease = LockLease.builder()
                .providerName("redis")
                .namespace("ns")
                .lockName("job:1")
                .lockKey("iron:lock:{ns:job:1}:lock")
                .ownerToken("token")
                .leaseTime(Duration.ofSeconds(30))
                .acquiredAt(Instant.now())
                .build();
        LockLeaseRef ref = LockLeaseRef.fromLease(lease);
        assertEquals("token", LockRenewRequest.builder().leaseRef(ref).leaseTime(Duration.ofSeconds(30)).build().getOwnerToken());
        assertEquals("job:1", LockReleaseRequest.builder().leaseRef(ref).build().getLockName());
        assertEquals("ns", LockCheckRequest.builder().leaseRef(ref).build().getNamespace());
    }
}
