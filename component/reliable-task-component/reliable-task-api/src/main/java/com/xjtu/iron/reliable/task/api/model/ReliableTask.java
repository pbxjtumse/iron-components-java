package com.xjtu.iron.reliable.task.api.model;

import com.xjtu.iron.reliable.task.api.state.ReliableTaskStatus;

import java.time.Instant;
import java.util.Objects;

/** 一条持久化任务的不可变快照。 */
public final class ReliableTask {

    /** 任务的全局定位键，由存储域、命名空间和任务 ID 组成。 */
    private final ReliableTaskKey key;
    /** 任务类型，用于选择对应的业务处理器。 */
    private final String taskType;
    /** 业务侧关联键，便于按业务单据追踪任务。 */
    private final String businessKey;
    /** 任务处理器消费的序列化业务载荷。 */
    private final String payload;
    /** 存储路由键，用于未来的分库分表定位。 */
    private final String routeKey;
    /** 扫描桶编号，用于拆分扫描范围。 */
    private final int scanBucket;
    /** 任务当前所处的生命周期状态。 */
    private final ReliableTaskStatus status;
    /** 已发起的执行尝试次数。 */
    private final int attemptCount;
    /** 允许发起的最大执行尝试次数。 */
    private final int maxAttempts;
    /** 任务下一次允许被执行的时间。 */
    private final Instant nextExecuteAt;
    /** 当前持有任务租约的执行者标识。 */
    private final String ownerId;
    /** 当前执行租约的失效时间。 */
    private final Instant leaseUntil;
    /** 用于并发控制和状态迁移 CAS 的版本号。 */
    private final long version;
    /** 最近一次执行失败的错误编码。 */
    private final String lastErrorCode;
    /** 最近一次执行失败的错误说明。 */
    private final String lastErrorMessage;
    /** 任务首次创建时间。 */
    private final Instant createdAt;
    /** 任务最近一次更新时间。 */
    private final Instant updatedAt;
    /** 任务成功完成时间；未完成时为空。 */
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
    public String getStoreName() { return key.getStoreName(); }
    public String getNamespace() { return key.getNamespace(); }
    public String getTaskId() { return key.getTaskId(); }
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
