package com.xjtu.iron.distributed.lock.core.fencing.flow;

import com.xjtu.iron.distributed.lock.api.model.LockHandle;
import com.xjtu.iron.distributed.lock.api.model.LockResult;
import com.xjtu.iron.distributed.lock.spi.protocol.common.LockLease;

import java.util.Objects;
import java.util.Optional;

/** fencing flow 执行结果。 */
public final class FencingCompletion {

    private final LockLease lease;
    private final LockResult<LockHandle> failureResult;

    private FencingCompletion(LockLease lease, LockResult<LockHandle> failureResult) {
        this.lease = lease;
        this.failureResult = failureResult;
        if ((lease == null) == (failureResult == null)) {
            throw new IllegalArgumentException("exactly one of lease or failureResult must be provided");
        }
    }

    public static FencingCompletion success(LockLease lease) {
        return new FencingCompletion(Objects.requireNonNull(lease, "lease must not be null"), null);
    }

    public static FencingCompletion failure(LockResult<LockHandle> failureResult) {
        return new FencingCompletion(null, Objects.requireNonNull(failureResult, "failureResult must not be null"));
    }

    public boolean isSuccess() { return lease != null; }
    public Optional<LockLease> lease() { return Optional.ofNullable(lease); }
    public Optional<LockResult<LockHandle>> failureResult() { return Optional.ofNullable(failureResult); }

    public LockLease requireLease() {
        if (lease == null) {
            throw new IllegalStateException("fencing completion is failure");
        }
        return lease;
    }

    public LockResult<LockHandle> requireFailureResult() {
        if (failureResult == null) {
            throw new IllegalStateException("fencing completion is success");
        }
        return failureResult;
    }
}
