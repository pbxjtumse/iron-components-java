package com.xjtu.iron.reliable.task.api.repository;

import com.xjtu.iron.reliable.task.api.model.ReliableTaskKey;

import java.time.Instant;

/** 当前 owner 的 Lease 条件续租请求。 */
public record ReliableTaskLeaseRenewCommand(
        ReliableTaskKey key,
        String ownerId,
        long expectedVersion,
        Instant now,
        Instant leaseUntil) {

    public ReliableTaskLeaseRenewCommand {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        ownerId = ownerId.trim();
        if (expectedVersion < 1) {
            throw new IllegalArgumentException("expectedVersion must be positive");
        }
        if (now == null || leaseUntil == null || !leaseUntil.isAfter(now)) {
            throw new IllegalArgumentException("leaseUntil must be after now");
        }
    }
}
