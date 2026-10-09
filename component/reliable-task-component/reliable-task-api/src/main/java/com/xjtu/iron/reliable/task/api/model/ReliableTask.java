package com.xjtu.iron.reliable.task.api.model;

import com.xjtu.iron.reliable.task.api.state.ReliableTaskStatus;

import java.time.Instant;
import java.util.Objects;

/** 一条持久化任务的不可变快照。 */
public final class ReliableTask {

    private final ReliableTaskKey key;
    private final String taskType;
    private final String businessKey;
    private final String payload;
    private final String routeKey;
    private final int scanBucket;
    private final ReliableTaskStatus status;
    private final int attemptCount;
    private final int maxAttempts;
    private final Instant nextExecuteAt;
    private final String ownerId;
    private final Instant leaseUntil;
    private final long version;
    private final String lastErrorCode;
    private final String lastErrorMessage;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant completedAt;

    public ReliableTask(
            ReliableTaskKey key,
            String taskType,
            String businessKey,
            String payload,
            String routeKey,
            int scanBucket,
            ReliableTaskStatus status,
            int attemptCount,
            int maxAttempts,
            Instant nextExecuteAt,
            String ownerId,
            Instant leaseUntil,
            long version,
            String lastErrorCode,
            String lastErrorMessage,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt) {
        this.key = Objects.requireNonNull(key, "key must not be null");
        this.taskType = requireText(taskType, "taskType");
        this.businessKey = normalize(businessKey);
        this.payload = payload == null ? "" : payload;
        this.routeKey = normalize(routeKey);
        if (scanBucket < 0) {
            throw new IllegalArgumentException("scanBucket must not be negative");
        }
        this.scanBucket = scanBucket;
        this.status = Objects.requireNonNull(status, "status must not be null");
        if (attemptCount < 0 || maxAttempts < 1 || attemptCount > maxAttempts) {
            throw new IllegalArgumentException("attempt count must be between zero and maxAttempts");
        }
        this.attemptCount = attemptCount;
        this.maxAttempts = maxAttempts;
        this.nextExecuteAt = nextExecuteAt;
        this.ownerId = normalize(ownerId);
        this.leaseUntil = leaseUntil;
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        this.version = version;
        this.lastErrorCode = normalize(lastErrorCode);
        this.lastErrorMessage = normalize(lastErrorMessage);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        this.completedAt = completedAt;
        validateOwnership();
    }

    private void validateOwnership() {
        if (status == ReliableTaskStatus.RUNNING) {
            if (ownerId == null || leaseUntil == null) {
                throw new IllegalArgumentException("RUNNING task requires ownerId and leaseUntil");
            }
            return;
        }
        if (ownerId != null || leaseUntil != null) {
            throw new IllegalArgumentException("non-RUNNING task must not retain owner or lease");
        }
    }

    public ReliableTaskKey getKey() { return key; }
    public String getStoreName() { return key.storeName(); }
    public String getNamespace() { return key.namespace(); }
    public String getTaskId() { return key.taskId(); }
    public String getTaskType() { return taskType; }
    public String getBusinessKey() { return businessKey; }
    public String getPayload() { return payload; }
    public String getRouteKey() { return routeKey; }
    public int getScanBucket() { return scanBucket; }
    public ReliableTaskStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public int getMaxAttempts() { return maxAttempts; }
    public Instant getNextExecuteAt() { return nextExecuteAt; }
    public String getOwnerId() { return ownerId; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public long getVersion() { return version; }
    public String getLastErrorCode() { return lastErrorCode; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }

    private static String requireText(String value, String name) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
