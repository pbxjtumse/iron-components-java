package com.xjtu.iron.idempotent.api.repository.recovery;

import com.xjtu.iron.idempotent.api.state.IdempotencyStatus;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;

import java.time.Instant;

/**
 * Reliable Task 可持久化/投递的恢复候选快照。
 *
 * <p>Candidate 是扫描时刻的只读快照，不是执行许可。外部任务投递后，真正执行前还要把
 * ownerToken/version 作为 expected 值传给 recover()，由 Repository 再次校验。</p>
 */
public final class IdempotencyRecoveryCandidate {

    /** 记录所在逻辑 Store。 */
    private final String storeName;

    /** 扫描桶。 */
    private final int scanBucket;

    /** 幂等隔离域。 */
    private final String namespace;

    /** 逻辑幂等 Key。 */
    private final String key;

    /** 业务路由元数据。 */
    private final String routeKey;

    /** 请求业务内容指纹。 */
    private final String requestHash;

    /** 扫描时观察到的持久状态。 */
    private final IdempotencyStatus status;

    /** 扫描时观察到的 owner。 */
    private final String ownerToken;

    /** 扫描时观察到的 generation version。 */
    private final long version;

    /** PROCESSING 超时时间；FAILED candidate 可能为空。 */
    private final Instant processingExpireAt;

    /** retryable FAILED 的失败码，便于恢复任务记录原因。 */
    private final String failureCode;

    public IdempotencyRecoveryCandidate(String storeName, int scanBucket, String namespace, String key, String routeKey, String requestHash,
                                        IdempotencyStatus status, String ownerToken, long version, Instant processingExpireAt, String failureCode) {
        this.storeName = storeName;
        this.scanBucket = scanBucket;
        this.namespace = namespace;
        this.key = key;
        this.routeKey = routeKey;
        this.requestHash = requestHash;
        this.status = status;
        this.ownerToken = ownerToken;
        this.version = version;
        this.processingExpireAt = processingExpireAt;
        this.failureCode = failureCode;
    }

    public String getStoreName() { return storeName; }
    public int getScanBucket() { return scanBucket; }
    public String getNamespace() { return namespace; }
    public String getKey() { return key; }
    public String getRouteKey() { return routeKey; }
    public String getRequestHash() { return requestHash; }
    public IdempotencyStatus getStatus() { return status; }
    public String getOwnerToken() { return ownerToken; }
    public long getVersion() { return version; }
    public Instant getProcessingExpireAt() { return processingExpireAt; }
    public String getFailureCode() { return failureCode; }

    public IdempotencyStorageContext storageContext() {
        return IdempotencyStorageContext.of(storeName, scanBucket);
    }
}
