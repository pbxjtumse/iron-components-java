package com.xjtu.iron.idempotent.api.repository;

import com.xjtu.iron.idempotent.api.policy.IdempotencyWindowPolicy;
import com.xjtu.iron.idempotent.api.recovery.IdempotencyRecoveryMode;
import com.xjtu.iron.idempotent.api.state.IdempotencyStatus;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;

import java.time.Instant;

/**
 * Repository 中的一条不可变幂等状态快照。
 *
 * <p>这是真正的“Idempotency State”。它记录的是某个逻辑请求历史上执行到哪里，
 * 而不是分布式锁当前是否被持有。</p>
 *
 * <p>storeName / scanBucket 描述逻辑存储域与恢复扫描分桶；物理分片身份由 Storage Routing 作用域负责。</p>
 *
 * <p>其中最关键的组合是 {@code status + ownerToken + version + processingExpireAt}：
 * status 描述业务阶段，ownerToken/version 描述当前 generation，processingExpireAt 描述执行权租约。</p>
 */
public final class IdempotencyRecord {

    /** 逻辑 Store 名称；例如 message-consume / payment。 */
    private final String storeName;

    /** Reliable Recovery 使用的稳定逻辑扫描桶，不等于物理表号。 */
    private final int scanBucket;

    /** 业务隔离域。 */
    private final String namespace;

    /** 逻辑幂等 Key。 */
    private final String key;

    /** 业务路由元数据，例如租户/商户路由；不再承担存储分片职责。 */
    private final String routeKey;

    /** 请求业务指纹，用于识别同 key 不同内容的误用。 */
    private final String requestHash;

    /** 真正持久状态。 */
    private final IdempotencyStatus status;

    /** 当前 generation owner。 */
    private final String ownerToken;

    /** generation 版本；恢复/窗口重启时递增。 */
    private final long version;

    /** 可选成功结果快照。 */
    private final String resultPayload;

    /** 失败稳定码。 */
    private final String failureCode;

    /** 失败描述。 */
    private final String failureMessage;

    /** 是否允许后续显式恢复。 */
    private final boolean failureRetryable;

    /** NONE / EXTERNAL_TASK。 */
    private final IdempotencyRecoveryMode recoveryMode;

    /** WINDOWED 窗口推进策略。 */
    private final IdempotencyWindowPolicy windowPolicy;

    /** 当前 PROCESSING generation 的执行权过期时间。 */
    private final Instant processingExpireAt;

    /** WINDOWED 语义窗口结束时间。 */
    private final Instant windowExpireAt;

    /** Redis/DB 记录可物理清理的时间；不继续承担幂等语义。 */
    private final Instant retentionExpireAt;

    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant completedAt;

    private IdempotencyRecord(Builder builder) {
        this.storeName = builder.storeName;
        this.scanBucket = builder.scanBucket;
        this.namespace = builder.namespace;
        this.key = builder.key;
        this.routeKey = builder.routeKey;
        this.requestHash = builder.requestHash;
        this.status = builder.status;
        this.ownerToken = builder.ownerToken;
        this.version = builder.version;
        this.resultPayload = builder.resultPayload;
        this.failureCode = builder.failureCode;
        this.failureMessage = builder.failureMessage;
        this.failureRetryable = builder.failureRetryable;
        this.recoveryMode = builder.recoveryMode;
        this.windowPolicy = builder.windowPolicy;
        this.processingExpireAt = builder.processingExpireAt;
        this.windowExpireAt = builder.windowExpireAt;
        this.retentionExpireAt = builder.retentionExpireAt;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
        this.completedAt = builder.completedAt;
    }

    public static Builder builder() { return new Builder(); }

    public String getStoreName() { return storeName; }
    public int getScanBucket() { return scanBucket; }
    public String getNamespace() { return namespace; }
    public String getKey() { return key; }
    public String getRouteKey() { return routeKey; }
    public String getRequestHash() { return requestHash; }
    public IdempotencyStatus getStatus() { return status; }
    public String getOwnerToken() { return ownerToken; }
    public long getVersion() { return version; }
    public String getResultPayload() { return resultPayload; }
    public String getFailureCode() { return failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public boolean isFailureRetryable() { return failureRetryable; }
    public IdempotencyRecoveryMode getRecoveryMode() { return recoveryMode; }
    public IdempotencyWindowPolicy getWindowPolicy() { return windowPolicy; }
    public Instant getProcessingExpireAt() { return processingExpireAt; }
    public Instant getWindowExpireAt() { return windowExpireAt; }
    public Instant getRetentionExpireAt() { return retentionExpireAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }

    public IdempotencyStorageContext storageContext() {
        return IdempotencyStorageContext.of(storeName == null ? IdempotencyStorageContext.DEFAULT_STORE_NAME : storeName, scanBucket);
    }

    public static final class Builder {
        private String storeName = IdempotencyStorageContext.DEFAULT_STORE_NAME;
        private int scanBucket;
        private String namespace;
        private String key;
        private String routeKey;
        private String requestHash;
        private IdempotencyStatus status;
        private String ownerToken;
        private long version;
        private String resultPayload;
        private String failureCode;
        private String failureMessage;
        private boolean failureRetryable;
        private IdempotencyRecoveryMode recoveryMode;
        private IdempotencyWindowPolicy windowPolicy;
        private Instant processingExpireAt;
        private Instant windowExpireAt;
        private Instant retentionExpireAt;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant completedAt;

        public Builder storeName(String value) { this.storeName = value; return this; }
        public Builder scanBucket(int value) { this.scanBucket = value; return this; }
        public Builder namespace(String value) { this.namespace = value; return this; }
        public Builder key(String value) { this.key = value; return this; }
        public Builder routeKey(String value) { this.routeKey = value; return this; }
        public Builder requestHash(String value) { this.requestHash = value; return this; }
        public Builder status(IdempotencyStatus value) { this.status = value; return this; }
        public Builder ownerToken(String value) { this.ownerToken = value; return this; }
        public Builder version(long value) { this.version = value; return this; }
        public Builder resultPayload(String value) { this.resultPayload = value; return this; }
        public Builder failureCode(String value) { this.failureCode = value; return this; }
        public Builder failureMessage(String value) { this.failureMessage = value; return this; }
        public Builder failureRetryable(boolean value) { this.failureRetryable = value; return this; }
        public Builder recoveryMode(IdempotencyRecoveryMode value) { this.recoveryMode = value; return this; }
        public Builder windowPolicy(IdempotencyWindowPolicy value) { this.windowPolicy = value; return this; }
        public Builder processingExpireAt(Instant value) { this.processingExpireAt = value; return this; }
        public Builder windowExpireAt(Instant value) { this.windowExpireAt = value; return this; }
        public Builder retentionExpireAt(Instant value) { this.retentionExpireAt = value; return this; }
        public Builder createdAt(Instant value) { this.createdAt = value; return this; }
        public Builder updatedAt(Instant value) { this.updatedAt = value; return this; }
        public Builder completedAt(Instant value) { this.completedAt = value; return this; }

        public IdempotencyRecord build() {
            if (scanBucket < 0) {
                throw new IllegalArgumentException("scanBucket must not be negative");
            }
            return new IdempotencyRecord(this);
        }
    }
}
