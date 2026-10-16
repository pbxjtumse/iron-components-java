package com.xjtu.iron.idempotent.api.recovery;

import com.xjtu.iron.idempotent.api.policy.IdempotencyPolicy;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;

/**
 * 外部 Reliable Task 调用 recover() 时使用的请求。
 *
 * <p>真正关键的是 {@code expectedOwnerToken + expectedVersion}：扫描 candidate 只能说明“扫描当时看到 A/10 需要恢复”，
 * 真正执行任务时仍必须再次由 Repository 校验 current 是否还是 A/10。若已经变成 B/11，则返回 STALE_CANDIDATE。</p>
 */
public final class IdempotencyRecoveryRequest {

    /** 待恢复的逻辑幂等 Key，来自扫描 candidate。 */
    private final String key;

    /** 首次请求的业务内容指纹；恢复时用于再次防止同 key 不同内容误用。 */
    private final String requestHash;

    /** 首次请求的业务路由元数据；恢复任务必须沿用，不能重新计算成别的路由。 */
    private final String routeKey;

    /** 待恢复记录所在逻辑 Store。 */
    private final String storeName;

    /** 待恢复记录所属扫描桶。 */
    private final int scanBucket;

    /** 扫描 candidate 时看到的 owner；为空表示只校验 expectedVersion。 */
    private final String expectedOwnerToken;

    /** 扫描 candidate 时看到的 version；为空表示只校验 expectedOwnerToken。 */
    private final Long expectedVersion;

    /** 恢复链路使用的命名 Policy，通常与正常 execute() 相同。 */
    private final String policyName;

    /** 内联恢复策略，优先于 policyName，主要用于测试或特殊调用。 */
    private final IdempotencyPolicy policy;

    private IdempotencyRecoveryRequest(Builder builder) {
        this.key = builder.key;
        this.requestHash = builder.requestHash;
        this.routeKey = builder.routeKey;
        this.storeName = builder.storeName == null || builder.storeName.isBlank()
                ? IdempotencyStorageContext.DEFAULT_STORE_NAME : builder.storeName.trim();
        this.scanBucket = builder.scanBucket;
        this.expectedOwnerToken = builder.expectedOwnerToken;
        this.expectedVersion = builder.expectedVersion;
        this.policyName = builder.policyName;
        this.policy = builder.policy;
    }

    public static Builder builder() { return new Builder(); }

    public String getKey() { return key; }
    public String getRequestHash() { return requestHash; }
    public String getRouteKey() { return routeKey; }
    public String getStoreName() { return storeName; }
    public int getScanBucket() { return scanBucket; }
    public String getExpectedOwnerToken() { return expectedOwnerToken; }
    public Long getExpectedVersion() { return expectedVersion; }
    public String getPolicyName() { return policyName; }
    public IdempotencyPolicy getPolicy() { return policy; }

    public IdempotencyStorageContext storageContext() {
        return IdempotencyStorageContext.of(storeName, scanBucket);
    }

    public static final class Builder {
        /** 待恢复的逻辑幂等 Key。 */
        private String key;

        /** 待恢复请求的业务内容指纹。 */
        private String requestHash;

        /** 待恢复请求的业务路由元数据。 */
        private String routeKey;

        /** 逻辑 Store，默认 default。 */
        private String storeName = IdempotencyStorageContext.DEFAULT_STORE_NAME;

        /** 扫描桶，不能为负数。 */
        private int scanBucket;

        /** candidate 中观察到的 ownerToken。 */
        private String expectedOwnerToken;

        /** candidate 中观察到的 generation version。 */
        private Long expectedVersion;

        /** 命名 Policy。 */
        private String policyName;

        /** 内联 Policy。 */
        private IdempotencyPolicy policy;

        public Builder key(String value) { this.key = value; return this; }
        public Builder requestHash(String value) { this.requestHash = value; return this; }
        public Builder routeKey(String value) { this.routeKey = value; return this; }
        public Builder storeName(String value) { this.storeName = value; return this; }
        public Builder scanBucket(int value) { this.scanBucket = value; return this; }
        public Builder expectedOwnerToken(String value) { this.expectedOwnerToken = value; return this; }
        public Builder expectedVersion(Long value) { this.expectedVersion = value; return this; }
        public Builder policyName(String value) { this.policyName = value; return this; }
        public Builder policy(IdempotencyPolicy value) { this.policy = value; return this; }

        public IdempotencyRecoveryRequest build() {
            if (scanBucket < 0) {
                throw new IllegalArgumentException("scanBucket must not be negative");
            }
            return new IdempotencyRecoveryRequest(this);
        }
    }
}
