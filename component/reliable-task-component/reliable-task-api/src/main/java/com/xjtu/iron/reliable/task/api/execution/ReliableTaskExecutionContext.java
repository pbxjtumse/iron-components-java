package com.xjtu.iron.reliable.task.api.execution;

import com.xjtu.iron.reliable.task.api.model.ReliableTask;
import com.xjtu.iron.reliable.task.api.model.ReliableTaskStatus;

import java.time.Duration;
import java.util.Objects;

/** 一轮持久化执行的上下文。 */
public final class ReliableTaskExecutionContext {

    private final ReliableTask task;
    private final ReliableTaskStatus claimedFromStatus;
    private final ReliableTaskLeaseRenewer leaseRenewer;

    public ReliableTaskExecutionContext(
            ReliableTask task,
            ReliableTaskStatus claimedFromStatus,
            ReliableTaskLeaseRenewer leaseRenewer) {
        this.task = Objects.requireNonNull(task, "task must not be null");
        this.claimedFromStatus = Objects.requireNonNull(claimedFromStatus, "claimedFromStatus must not be null");
        this.leaseRenewer = Objects.requireNonNull(leaseRenewer, "leaseRenewer must not be null");
    }

    public ReliableTask getTask() { return task; }
    public ReliableTaskStatus getClaimedFromStatus() { return claimedFromStatus; }

    public boolean renewLease(Duration extension) {
        Objects.requireNonNull(extension, "extension must not be null");
        if (extension.isZero() || extension.isNegative()) {
            throw new IllegalArgumentException("extension must be positive");
        }
        return leaseRenewer.renew(extension);
    }
}
