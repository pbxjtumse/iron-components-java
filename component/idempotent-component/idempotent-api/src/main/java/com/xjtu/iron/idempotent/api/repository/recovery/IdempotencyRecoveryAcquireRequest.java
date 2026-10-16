package com.xjtu.iron.idempotent.api.repository.recovery;

import com.xjtu.iron.idempotent.api.policy.IdempotencyMode;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;

import java.time.Duration;
import java.time.Instant;

/**
 * Reliable Task 恢复抢占请求。
 *
 * <p>恢复不是“看到超时就执行”，而是用 expectedOwnerToken/expectedVersion 对扫描快照做二次 CAS。
 * 只有当前记录仍与 candidate 一致，Provider 才能写入 newOwnerToken 并把 version + 1。</p>
 */
public final class IdempotencyRecoveryAcquireRequest {

    /** 待恢复记录的逻辑存储上下文。 */
    private final IdempotencyStorageContext storageContext;

    /** 幂等隔离域。 */
    private final String namespace;

    /** 逻辑幂等 Key。 */
    private final String key;

    /** 请求业务内容指纹。 */
    private final String requestHash;

    /** 业务路由元数据。 */
    private final String routeKey;

    /** 恢复成功后写入的新 generation owner。 */
    private final String newOwnerToken;

    /** candidate 中看到的旧 owner；为空则不参与校验。 */
    private final String expectedOwnerToken;

    /** candidate 中看到的旧 version；为空则不参与校验。 */
    private final Long expectedVersion;

    /** 幂等模式。 */
    private final IdempotencyMode mode;

    /** 新 generation 的 PROCESSING 租约时长。 */
    private final Duration processingTimeout;

    /** 是否允许接管已过期的 PROCESSING。 */
    private final boolean recoverProcessingTimeout;

    /** 是否允许接管 retryable FAILED。 */
    private final boolean recoverFailed;

    /** Core 传入的统一当前时间。 */
    private final Instant now;

    public IdempotencyRecoveryAcquireRequest(IdempotencyStorageContext storageContext, String namespace, String key, String requestHash,
                                             String routeKey, String newOwnerToken, String expectedOwnerToken, Long expectedVersion,
                                             IdempotencyMode mode, Duration processingTimeout, boolean recoverProcessingTimeout,
                                             boolean recoverFailed, Instant now) {
        this.storageContext = storageContext;
        this.namespace = namespace;
        this.key = key;
        this.requestHash = requestHash;
        this.routeKey = routeKey;
        this.newOwnerToken = newOwnerToken;
        this.expectedOwnerToken = expectedOwnerToken;
        this.expectedVersion = expectedVersion;
        this.mode = mode;
        this.processingTimeout = processingTimeout;
        this.recoverProcessingTimeout = recoverProcessingTimeout;
        this.recoverFailed = recoverFailed;
        this.now = now;
    }

    public IdempotencyStorageContext getStorageContext() { return storageContext; }
    public String getNamespace() { return namespace; }
    public String getKey() { return key; }
    public String getRequestHash() { return requestHash; }
    public String getRouteKey() { return routeKey; }
    public String getNewOwnerToken() { return newOwnerToken; }
    public String getExpectedOwnerToken() { return expectedOwnerToken; }
    public Long getExpectedVersion() { return expectedVersion; }
    public IdempotencyMode getMode() { return mode; }
    public Duration getProcessingTimeout() { return processingTimeout; }
    public boolean isRecoverProcessingTimeout() { return recoverProcessingTimeout; }
    public boolean isRecoverFailed() { return recoverFailed; }
    public Instant getNow() { return now; }
}
