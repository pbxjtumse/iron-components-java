package com.xjtu.iron.reliable.task.api.repository.transition;

import com.xjtu.iron.reliable.task.api.model.ReliableTaskKey;
import com.xjtu.iron.reliable.task.api.state.ReliableTaskStatus;

import java.time.Instant;
import java.util.Objects;

/** 人工重新入队或取消时使用的 version CAS 更新。 */
public final class ReliableTaskAdminTransitionCommand {

    /** 需要执行人工状态迁移的任务主键。 */
    private final ReliableTaskKey key;

    /** 人工操作读取任务时观察到的状态。 */
    private final ReliableTaskStatus expectedStatus;

    /** 人工操作读取任务时观察到的版本。 */
    private final long expectedVersion;

    /** 人工操作希望迁移到的目标状态。 */
    private final ReliableTaskStatus targetStatus;

    /** 重新进入自动执行时的下一次可执行时间。 */
    private final Instant nextExecuteAt;

    /** 是否在重新入队时清零持久化执行次数。 */
    private final boolean resetAttempts;

    /** 执行人工状态迁移时的当前时间。 */
    private final Instant now;

    public ReliableTaskAdminTransitionCommand(
            ReliableTaskKey key,
            ReliableTaskStatus expectedStatus,
            long expectedVersion,
            ReliableTaskStatus targetStatus,
            Instant nextExecuteAt,
            boolean resetAttempts,
            Instant now) {
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
        this.key = key;
        this.expectedStatus = expectedStatus;
        this.expectedVersion = expectedVersion;
        this.targetStatus = targetStatus;
        this.nextExecuteAt = nextExecuteAt;
        this.resetAttempts = resetAttempts;
        this.now = now;
    }

    public ReliableTaskKey getKey() {
        return key;
    }

    public ReliableTaskStatus getExpectedStatus() {
        return expectedStatus;
    }

    public long getExpectedVersion() {
        return expectedVersion;
    }

    public ReliableTaskStatus getTargetStatus() {
        return targetStatus;
    }

    public Instant getNextExecuteAt() {
        return nextExecuteAt;
    }

    public boolean isResetAttempts() {
        return resetAttempts;
    }

    public Instant getNow() {
        return now;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ReliableTaskAdminTransitionCommand that)) {
            return false;
        }
        return expectedVersion == that.expectedVersion
                && resetAttempts == that.resetAttempts
                && key.equals(that.key)
                && expectedStatus == that.expectedStatus
                && targetStatus == that.targetStatus
                && Objects.equals(nextExecuteAt, that.nextExecuteAt)
                && now.equals(that.now);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                key,
                expectedStatus,
                expectedVersion,
                targetStatus,
                nextExecuteAt,
                resetAttempts,
                now
        );
    }

    @Override
    public String toString() {
        return "ReliableTaskAdminTransitionCommand{" +
                "key=" + key +
                ", expectedStatus=" + expectedStatus +
                ", expectedVersion=" + expectedVersion +
                ", targetStatus=" + targetStatus +
                ", nextExecuteAt=" + nextExecuteAt +
                ", resetAttempts=" + resetAttempts +
                ", now=" + now +
                '}';
    }
}
