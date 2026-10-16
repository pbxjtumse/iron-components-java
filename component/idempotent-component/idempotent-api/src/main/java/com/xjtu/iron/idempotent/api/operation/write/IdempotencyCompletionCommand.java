package com.xjtu.iron.idempotent.api.operation.write;

import com.xjtu.iron.idempotent.api.policy.IdempotencyPolicy;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;

/**
 * SUCCESS / DISCARDED 共用的低层终态命令。
 *
 * <p>调用方必须继续携带 acquire 时的 ownerToken + version；它们共同证明“我仍然是当前 generation”。</p>
 */
public final class IdempotencyCompletionCommand {

    /** 逻辑幂等 Key，必须与 acquire 命令一致。 */
    private final String key;

    /** 逻辑存储上下文，必须与 acquire 命令一致。 */
    private final IdempotencyStorageContext storageContext;

    /** acquire 获得的 ownerToken。 */
    private final String ownerToken;

    /** acquire 返回记录中的 generation version。 */
    private final long version;

    /** SUCCESS/DISCARDED 可选结果载荷；是否可回放由调用方自己的协议决定。 */
    private final String resultPayload;

    /** 命名 Policy。 */
    private final String policyName;

    /** 内联 Policy，优先于 policyName。 */
    private final IdempotencyPolicy policy;

    private IdempotencyCompletionCommand(Builder builder) {
        this.key = builder.key;
        this.storageContext = builder.storageContext;
        this.ownerToken = builder.ownerToken;
        this.version = builder.version;
        this.resultPayload = builder.resultPayload;
        this.policyName = builder.policyName;
        this.policy = builder.policy;
    }

    public static Builder builder() { return new Builder(); }

    public String getKey() { return key; }
    public IdempotencyStorageContext getStorageContext() { return storageContext; }
    public String getOwnerToken() { return ownerToken; }
    public long getVersion() { return version; }
    public String getResultPayload() { return resultPayload; }
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

        /** 要保存的结果载荷。 */
        private String resultPayload;

        /** 命名 Policy。 */
        private String policyName;

        /** 内联 Policy。 */
        private IdempotencyPolicy policy;

        public Builder key(String value) { this.key = value; return this; }
        public Builder storageContext(IdempotencyStorageContext value) { this.storageContext = value; return this; }
        public Builder ownerToken(String value) { this.ownerToken = value; return this; }
        public Builder version(long value) { this.version = value; return this; }
        public Builder resultPayload(String value) { this.resultPayload = value; return this; }
        public Builder policyName(String value) { this.policyName = value; return this; }
        public Builder policy(IdempotencyPolicy value) { this.policy = value; return this; }
        public IdempotencyCompletionCommand build() { return new IdempotencyCompletionCommand(this); }
    }
}
