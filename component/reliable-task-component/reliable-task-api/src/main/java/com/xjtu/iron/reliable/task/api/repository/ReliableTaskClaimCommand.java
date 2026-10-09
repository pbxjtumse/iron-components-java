package com.xjtu.iron.reliable.task.api.repository;

import com.xjtu.iron.reliable.task.api.model.ReliableTask;

import java.time.Instant;

/** 使用扫描快照的 version 对任务进行二次 CAS 抢占。 */
public record ReliableTaskClaimCommand(
        ReliableTask candidate,
        String ownerId,
        Instant now,
        Instant leaseUntil) {

    public ReliableTaskClaimCommand {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate must not be null");
        }
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        ownerId = ownerId.trim();
        if (now == null || leaseUntil == null || !leaseUntil.isAfter(now)) {
            throw new IllegalArgumentException("leaseUntil must be after now");
        }
    }
}
