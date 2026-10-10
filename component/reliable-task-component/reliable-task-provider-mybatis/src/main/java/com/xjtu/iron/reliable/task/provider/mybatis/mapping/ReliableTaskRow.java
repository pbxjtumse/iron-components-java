package com.xjtu.iron.reliable.task.provider.mybatis.mapping;

import com.xjtu.iron.reliable.task.api.model.ReliableTask;
import com.xjtu.iron.reliable.task.api.model.ReliableTaskKey;
import com.xjtu.iron.reliable.task.api.state.ReliableTaskStatus;

import java.time.Instant;

/** MyBatis ResultMap 使用的数据库行对象，不对 Provider 外暴露。 */
public final class ReliableTaskRow {

    /** 任务所属的逻辑存储域。 */
    private String storeName;
    /** 任务所属的业务命名空间。 */
    private String namespace;
    /** 命名空间内唯一的任务编号。 */
    private String taskId;
    /** 用于选择业务处理器的任务类型。 */
    private String taskType;
    /** 关联原始业务单据的业务键。 */
    private String businessKey;
    /** 任务处理器消费的序列化业务载荷。 */
    private String payload;
    /** 用于定位物理分片的存储路由键。 */
    private String routeKey;
    /** 用于拆分扫描范围的桶编号。 */
    private int scanBucket;
    /** 任务当前持久化状态。 */
    private ReliableTaskStatus status;
    /** 已发起的持久化执行轮次。 */
    private int attemptCount;
    /** 允许发起的最大执行轮次。 */
    private int maxAttempts;
    /** 下一次允许执行的时间。 */
    private Instant nextExecuteAt;
    /** 当前租约所有者标识。 */
    private String ownerId;
    /** 当前租约失效时间。 */
    private Instant leaseUntil;
    /** 用于状态迁移 CAS 的版本号。 */
    private long version;
    /** 最近一次失败的错误编码。 */
    private String lastErrorCode;
    /** 最近一次失败的错误说明。 */
    private String lastErrorMessage;
    /** 任务创建时间。 */
    private Instant createdAt;
    /** 任务最近更新时间。 */
    private Instant updatedAt;
    /** 任务成功或终止完成时间。 */
    private Instant completedAt;

    public ReliableTask toDomain() {
        return new ReliableTask(
                new ReliableTaskKey(storeName, namespace, taskId),
                taskType,
                businessKey,
                payload,
                routeKey,
                scanBucket,
                status,
                attemptCount,
                maxAttempts,
                nextExecuteAt,
                ownerId,
                leaseUntil,
                version,
                lastErrorCode,
                lastErrorMessage,
                createdAt,
                updatedAt,
                completedAt
        );
    }

    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getBusinessKey() { return businessKey; }
    public void setBusinessKey(String businessKey) { this.businessKey = businessKey; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getRouteKey() { return routeKey; }
    public void setRouteKey(String routeKey) { this.routeKey = routeKey; }
    public int getScanBucket() { return scanBucket; }
    public void setScanBucket(int scanBucket) { this.scanBucket = scanBucket; }
    public ReliableTaskStatus getStatus() { return status; }
    public void setStatus(ReliableTaskStatus status) { this.status = status; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public Instant getNextExecuteAt() { return nextExecuteAt; }
    public void setNextExecuteAt(Instant nextExecuteAt) { this.nextExecuteAt = nextExecuteAt; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(Instant leaseUntil) { this.leaseUntil = leaseUntil; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public void setLastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
