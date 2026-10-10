package com.xjtu.iron.idempotent.provider.mybatis.mapping;

import com.xjtu.iron.idempotent.api.policy.IdempotencyWindowPolicy;
import com.xjtu.iron.idempotent.api.recovery.IdempotencyRecoveryMode;
import com.xjtu.iron.idempotent.api.repository.IdempotencyRecord;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryCandidate;
import com.xjtu.iron.idempotent.api.state.IdempotencyStatus;

import java.time.Instant;

/** MyBatis ResultMap 与写入命令共用的幂等数据库行对象。 */
public final class IdempotencyRow {

    /** 逻辑存储名称。 */
    private String storeName;
    /** 恢复扫描桶编号。 */
    private int scanBucket;
    /** 幂等命名空间。 */
    private String namespace;
    /** 幂等业务键。 */
    private String idempotencyKey;
    /** 业务路由键。 */
    private String routeKey;
    /** 请求内容摘要。 */
    private String requestHash;
    /** 当前持久化状态。 */
    private IdempotencyStatus status;
    /** 当前执行代际的 owner token。 */
    private String ownerToken;
    /** 当前执行代际版本号。 */
    private long version;
    /** 成功或丢弃结果载荷。 */
    private String resultPayload;
    /** 最近失败编码。 */
    private String failureCode;
    /** 最近失败描述。 */
    private String failureMessage;
    /** 最近失败是否允许恢复重试。 */
    private boolean failureRetryable;
    /** 当前记录的恢复模式。 */
    private IdempotencyRecoveryMode recoveryMode;
    /** 当前记录的窗口策略。 */
    private IdempotencyWindowPolicy windowPolicy;
    /** PROCESSING 租约到期时间。 */
    private Instant processingExpireAt;
    /** 幂等语义窗口到期时间。 */
    private Instant windowExpireAt;
    /** 记录允许清理的时间。 */
    private Instant retentionExpireAt;
    /** 当前 generation 创建时间。 */
    private Instant createdAt;
    /** 最近更新时间。 */
    private Instant updatedAt;
    /** 成功或丢弃完成时间。 */
    private Instant completedAt;

    public IdempotencyRecord toRecord() {
        return IdempotencyRecord.builder()
                .storeName(storeName).scanBucket(scanBucket).namespace(namespace).key(idempotencyKey)
                .routeKey(routeKey).requestHash(requestHash).status(status).ownerToken(ownerToken)
                .version(version).resultPayload(resultPayload).failureCode(failureCode)
                .failureMessage(failureMessage).failureRetryable(failureRetryable)
                .recoveryMode(recoveryMode == null ? IdempotencyRecoveryMode.NONE : recoveryMode)
                .windowPolicy(windowPolicy == null
                        ? IdempotencyWindowPolicy.FIXED_FROM_FIRST_ACQUIRE : windowPolicy)
                .processingExpireAt(processingExpireAt).windowExpireAt(windowExpireAt)
                .retentionExpireAt(retentionExpireAt).createdAt(createdAt).updatedAt(updatedAt)
                .completedAt(completedAt).build();
    }

    public IdempotencyRecoveryCandidate toRecoveryCandidate() {
        return new IdempotencyRecoveryCandidate(
                storeName, scanBucket, namespace, idempotencyKey, routeKey, requestHash, status,
                ownerToken, version, processingExpireAt, failureCode
        );
    }

    public String getStoreName() { return storeName; }
    public void setStoreName(String value) { this.storeName = value; }
    public int getScanBucket() { return scanBucket; }
    public void setScanBucket(int value) { this.scanBucket = value; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String value) { this.namespace = value; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String value) { this.idempotencyKey = value; }
    public String getRouteKey() { return routeKey; }
    public void setRouteKey(String value) { this.routeKey = value; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String value) { this.requestHash = value; }
    public IdempotencyStatus getStatus() { return status; }
    public void setStatus(IdempotencyStatus value) { this.status = value; }
    public String getOwnerToken() { return ownerToken; }
    public void setOwnerToken(String value) { this.ownerToken = value; }
    public long getVersion() { return version; }
    public void setVersion(long value) { this.version = value; }
    public String getResultPayload() { return resultPayload; }
    public void setResultPayload(String value) { this.resultPayload = value; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String value) { this.failureCode = value; }
    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String value) { this.failureMessage = value; }
    public boolean isFailureRetryable() { return failureRetryable; }
    public void setFailureRetryable(boolean value) { this.failureRetryable = value; }
    public IdempotencyRecoveryMode getRecoveryMode() { return recoveryMode; }
    public void setRecoveryMode(IdempotencyRecoveryMode value) { this.recoveryMode = value; }
    public IdempotencyWindowPolicy getWindowPolicy() { return windowPolicy; }
    public void setWindowPolicy(IdempotencyWindowPolicy value) { this.windowPolicy = value; }
    public Instant getProcessingExpireAt() { return processingExpireAt; }
    public void setProcessingExpireAt(Instant value) { this.processingExpireAt = value; }
    public Instant getWindowExpireAt() { return windowExpireAt; }
    public void setWindowExpireAt(Instant value) { this.windowExpireAt = value; }
    public Instant getRetentionExpireAt() { return retentionExpireAt; }
    public void setRetentionExpireAt(Instant value) { this.retentionExpireAt = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { this.createdAt = value; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant value) { this.updatedAt = value; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant value) { this.completedAt = value; }
}
