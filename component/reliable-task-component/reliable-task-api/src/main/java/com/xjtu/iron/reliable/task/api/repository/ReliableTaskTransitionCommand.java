package com.xjtu.iron.reliable.task.api.repository;

import com.xjtu.iron.reliable.task.api.model.ReliableTaskKey;
import com.xjtu.iron.reliable.task.api.model.ReliableTaskStatus;

import java.time.Instant;

/** 当前 owner 完成本轮执行时使用的条件状态更新。 */
public record ReliableTaskTransitionCommand(
        ReliableTaskKey key,
        String ownerId,
        long expectedVersion,
        ReliableTaskStatus targetStatus,
        Instant nextExecuteAt,
        String errorCode,
        String errorMessage,
        Instant now) {

    public ReliableTaskTransitionCommand {
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
        if (targetStatus == null || targetStatus == ReliableTaskStatus.RUNNING || targetStatus == ReliableTaskStatus.READY) {
            throw new IllegalArgumentException("invalid completion target status");
        }
        if ((targetStatus == ReliableTaskStatus.RETRY_WAIT || targetStatus == ReliableTaskStatus.WAIT_RECONCILE)
                && nextExecuteAt == null) {
            throw new IllegalArgumentException(targetStatus + " requires nextExecuteAt");
        }
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
    }
}
