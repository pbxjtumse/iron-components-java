package com.xjtu.iron.reliable.task.api.repository.transition;

import com.xjtu.iron.reliable.task.api.model.ReliableTaskKey;
import com.xjtu.iron.reliable.task.api.state.ReliableTaskStatus;

import java.time.Instant;
import java.util.Objects;

/** 当前 owner 完成本轮执行时使用的条件状态更新。 */
public final class ReliableTaskTransitionCommand {

    /** 需要完成状态迁移的任务主键。 */
    private final ReliableTaskKey key;

    /** 当前持有任务执行权的实例标识。 */
    private final String ownerId;

    /** 完成写入时必须匹配的任务版本。 */
    private final long expectedVersion;

    /** 本轮执行结束后希望写入的目标状态。 */
    private final ReliableTaskStatus targetStatus;

    /** RETRY_WAIT 或 WAIT_RECONCILE 的下一次可执行时间。 */
    private final Instant nextExecuteAt;

    /** 本轮执行失败或转人工时记录的稳定错误码。 */
    private final String errorCode;

    /** 本轮执行失败或转人工时记录的错误说明。 */
    private final String errorMessage;

    /** 完成本次状态迁移时的当前时间。 */
    private final Instant now;

    public ReliableTaskTransitionCommand(
            ReliableTaskKey key,
            String ownerId,
            long expectedVersion,
            ReliableTaskStatus targetStatus,
            Instant nextExecuteAt,
            String errorCode,
            String errorMessage,
            Instant now) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        if (expectedVersion < 1) {
            throw new IllegalArgumentException("expectedVersion must be positive");
        }
        if (targetStatus == null
                || targetStatus == ReliableTaskStatus.RUNNING
                || targetStatus == ReliableTaskStatus.READY) {
            throw new IllegalArgumentException("invalid completion target status");
        }
        if ((targetStatus == ReliableTaskStatus.RETRY_WAIT
                || targetStatus == ReliableTaskStatus.WAIT_RECONCILE)
                && nextExecuteAt == null) {
            throw new IllegalArgumentException(targetStatus + " requires nextExecuteAt");
        }
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        this.key = key;
        this.ownerId = ownerId.trim();
        this.expectedVersion = expectedVersion;
        this.targetStatus = targetStatus;
        this.nextExecuteAt = nextExecuteAt;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.now = now;
    }

    public ReliableTaskKey getKey() {
        return key;
    }

    public String getOwnerId() {
        return ownerId;
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

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getNow() {
        return now;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof ReliableTaskTransitionCommand that)) {
            return false;
        }
        return expectedVersion == that.expectedVersion
                && key.equals(that.key)
                && ownerId.equals(that.ownerId)
                && targetStatus == that.targetStatus
                && Objects.equals(nextExecuteAt, that.nextExecuteAt)
                && Objects.equals(errorCode, that.errorCode)
                && Objects.equals(errorMessage, that.errorMessage)
                && now.equals(that.now);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                key,
                ownerId,
                expectedVersion,
                targetStatus,
                nextExecuteAt,
                errorCode,
                errorMessage,
                now
        );
    }

    @Override
    public String toString() {
        return "ReliableTaskTransitionCommand{" +
                "key=" + key +
                ", ownerId='" + ownerId + '\'' +
                ", expectedVersion=" + expectedVersion +
                ", targetStatus=" + targetStatus +
                ", nextExecuteAt=" + nextExecuteAt +
                ", errorCode='" + errorCode + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", now=" + now +
                '}';
    }
}
