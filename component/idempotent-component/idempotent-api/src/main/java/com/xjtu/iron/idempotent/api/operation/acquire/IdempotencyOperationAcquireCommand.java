package com.xjtu.iron.idempotent.api.operation.acquire;

import com.xjtu.iron.idempotent.api.policy.IdempotencyPolicy;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;

/**
 * 低层 acquire 命令。
 *
 * <p>ownerToken 由调用方生成并持有，后续 markSuccess/markFailed/markDiscarded 必须继续使用该 owner 与 acquire 返回的 version。</p>
 */
public final class IdempotencyOperationAcquireCommand {

    /** 逻辑幂等 Key。 */
    private final String key;

    /** 业务内容指纹，用于冲突检测。 */
    private final String requestHash;

    /** 业务路由元数据。 */
    private final String routeKey;

    /** 逻辑存储上下文，低层调用方必须显式传入。 */
    private final IdempotencyStorageContext storageContext;

    /** 调用方生成并持有的 owner；只有 ACQUIRED 返回后才成为当前 generation owner。 */
    private final String ownerToken;

    /** 命名 Policy。 */
    private final String policyName;

    /** 内联 Policy，优先于 policyName。 */
    private final IdempotencyPolicy policy;

    private IdempotencyOperationAcquireCommand(Builder builder) {
        this.key = builder.key;
        this.requestHash = builder.requestHash;
        this.routeKey = builder.routeKey;
        this.storageContext = builder.storageContext;
        this.ownerToken = builder.ownerToken;
        this.policyName = builder.policyName;
        this.policy = builder.policy;
    }

    public static Builder builder() { return new Builder(); }

    public String getKey() { return key; }
    public String getRequestHash() { return requestHash; }
    public String getRouteKey() { return routeKey; }
    public IdempotencyStorageContext getStorageContext() { return storageContext; }
    public String getOwnerToken() { return ownerToken; }
    public String getPolicyName() { return policyName; }
    public IdempotencyPolicy getPolicy() { return policy; }

    public static final class Builder {
        /** 逻辑幂等 Key。 */
        private String key;

        /** 业务内容指纹。 */
        private String requestHash;

        /** 业务路由元数据。 */
        private String routeKey;

        /** 逻辑存储上下文。 */
        private IdempotencyStorageContext storageContext;

        /** 调用方生成的 ownerToken。 */
        private String ownerToken;

        /** 命名 Policy。 */
        private String policyName;

        /** 内联 Policy。 */
        private IdempotencyPolicy policy;

        public Builder key(String value) { this.key = value; return this; }
        public Builder requestHash(String value) { this.requestHash = value; return this; }
        public Builder routeKey(String value) { this.routeKey = value; return this; }
        public Builder storageContext(IdempotencyStorageContext value) { this.storageContext = value; return this; }
        public Builder ownerToken(String value) { this.ownerToken = value; return this; }
        public Builder policyName(String value) { this.policyName = value; return this; }
        public Builder policy(IdempotencyPolicy value) { this.policy = value; return this; }
        public IdempotencyOperationAcquireCommand build() { return new IdempotencyOperationAcquireCommand(this); }
    }
}
