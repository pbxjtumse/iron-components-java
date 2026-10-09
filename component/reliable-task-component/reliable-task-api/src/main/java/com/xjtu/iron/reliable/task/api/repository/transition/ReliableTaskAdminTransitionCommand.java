package com.xjtu.iron.reliable.task.api.repository.transition;

import com.xjtu.iron.reliable.task.api.model.ReliableTaskKey;
import com.xjtu.iron.reliable.task.api.state.ReliableTaskStatus;

import java.time.Instant;

/** 人工重新入队或取消时使用的 version CAS 更新。 */
public record ReliableTaskAdminTransitionCommand(
        ReliableTaskKey key,
        ReliableTaskStatus expectedStatus,
        long expectedVersion,
        ReliableTaskStatus targetStatus,
        Instant nextExecuteAt,
        boolean resetAttempts,
        Instant now) {

    public ReliableTaskAdminTransitionCommand {
        if (key == null || expectedStatus == null || targetStatus == null || now == null) {
            throw new IllegalArgumentException("admin transition fields must not be null");
        }
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
        }
        if (targetStatus == ReliableTaskStatus.RUNNING) {
            throw new IllegalArgumentException("admin transition cannot grant RUNNING ownership");
        }
        if (targetStatus == ReliableTaskStatus.READY && nextExecuteAt == null) {
            throw new IllegalArgumentException("READY requires nextExecuteAt");
        }
    }
}
