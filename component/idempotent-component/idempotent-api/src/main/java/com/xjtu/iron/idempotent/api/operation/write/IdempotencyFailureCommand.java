package com.xjtu.iron.idempotent.api.operation.write;

import com.xjtu.iron.idempotent.api.policy.IdempotencyPolicy;
import com.xjtu.iron.idempotent.api.repository.write.IdempotencyFailureInfo;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;

/**
 * FAILED 低层终态命令。
 *
 * <p>与 completion 命令一样，ownerToken + version 必须来自 acquire 返回的当前 generation；
 * 失败写入不是“强行覆盖状态”，而是一次受 CAS 保护的条件写。</p>
 */
public final class IdempotencyFailureCommand {

    /** 逻辑幂等 Key，必须与 acquire 命令一致。 */
    private final String key;

    /** 逻辑存储上下文，必须与 acquire 命令一致。 */
    private final IdempotencyStorageContext storageContext;

    /** acquire 获得的 ownerToken。 */
    private final String ownerToken;

    /** acquire 返回记录中的 generation version。 */
    private final long version;

    /** 失败分类结果，决定失败码、描述以及是否允许后续恢复。 */
    private final IdempotencyFailureInfo failure;

    /** 命名 Policy。 */
    private final String policyName;

    /** 内联 Policy，优先于 policyName。 */
    private final IdempotencyPolicy policy;

    private IdempotencyFailureCommand(Builder builder) {
        this.key = builder.key;
        this.storageContext = builder.storageContext;
        this.ownerToken = builder.ownerToken;
        this.version = builder.version;
        this.failure = builder.failure;
        this.policyName = builder.policyName;
        this.policy = builder.policy;
    }

    public static Builder builder() { return new Builder(); }

    public String getKey() { return key; }
    public IdempotencyStorageContext getStorageContext() { return storageContext; }
    public String getOwnerToken() { return ownerToken; }
    public long getVersion() { return version; }
    public IdempotencyFailureInfo getFailure() { return failure; }
    public String getPolicyName() { return policyName; }
    public IdempotencyPolicy getPolicy() { return policy; }

    public static final class Builder {
        /** 逻辑幂等 Key。 */
        private String key;

        /** 逻辑存储上下文。 */
        private IdempotencyStorageContext storageContext;

        /** acquire 时使用的 ownerToken。 */
        private String ownerToken;

        /** acquire 返回的 generation version。 */
        private long version;

        /** 失败分类结果。 */
        private IdempotencyFailureInfo failure;

        /** 命名 Policy。 */
        private String policyName;

        /** 内联 Policy。 */
        private IdempotencyPolicy policy;

        public Builder key(String value) { this.key = value; return this; }
        public Builder storageContext(IdempotencyStorageContext value) { this.storageContext = value; return this; }
        public Builder ownerToken(String value) { this.ownerToken = value; return this; }
        public Builder version(long value) { this.version = value; return this; }
        public Builder failure(IdempotencyFailureInfo value) { this.failure = value; return this; }
        public Builder policyName(String value) { this.policyName = value; return this; }
        public Builder policy(IdempotencyPolicy value) { this.policy = value; return this; }
        public IdempotencyFailureCommand build() { return new IdempotencyFailureCommand(this); }
    }
}
