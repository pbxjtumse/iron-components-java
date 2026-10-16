package com.xjtu.iron.idempotent.api.repository.acquire;

import com.xjtu.iron.idempotent.api.policy.IdempotencyMode;
import com.xjtu.iron.idempotent.api.policy.IdempotencyWindowPolicy;
import com.xjtu.iron.idempotent.api.recovery.IdempotencyRecoveryMode;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;

import java.time.Duration;
import java.time.Instant;

/**
 * Repository 普通抢占请求。
 *
 * <p>这是 Core 传给 Provider 的完整策略快照。Provider 必须在一次原子操作/事务中完成插入、冲突判断、
 * 窗口判断和状态派生，不能把“读记录”和“决定状态”拆成可并发穿插的两步。</p>
 */
public final class IdempotencyAcquireRequest {

    /** 逻辑存储上下文，参与 Provider key/SQL 条件。 */
    private final IdempotencyStorageContext storageContext;

    /** 幂等隔离域。 */
    private final String namespace;

    /** 逻辑幂等 Key。 */
    private final String key;

    /** 请求业务内容指纹。 */
    private final String requestHash;

    /** 业务路由元数据。 */
    private final String routeKey;

    /** 本次普通执行尝试生成的新 ownerToken。 */
    private final String ownerToken;

    /** 幂等模式，决定 DURABLE 或 WINDOWED 的判定规则。 */
    private final IdempotencyMode mode;

    /** 当前 PROCESSING generation 的租约时长。 */
    private final Duration processingTimeout;

    /** WINDOWED 模式下，同 key 仍算同一逻辑请求的时间窗口。 */
    private final Duration idempotencyWindow;

    /** WINDOWED 的窗口推进策略。 */
    private final IdempotencyWindowPolicy windowPolicy;

    /** 语义窗口结束后的额外物理保留时间。 */
    private final Duration recordRetentionTtl;

    /** 首次创建记录时写入的恢复模式。 */
    private final IdempotencyRecoveryMode recoveryMode;

    /** Core 传入的统一当前时间，Provider 不应自行取系统时间造成测试漂移。 */
    private final Instant now;

    public IdempotencyAcquireRequest(IdempotencyStorageContext storageContext, String namespace, String key, String requestHash, String routeKey,
                                     String ownerToken, IdempotencyMode mode, Duration processingTimeout, Duration idempotencyWindow,
                                     IdempotencyWindowPolicy windowPolicy, Duration recordRetentionTtl,
                                     IdempotencyRecoveryMode recoveryMode, Instant now) {
        this.storageContext = storageContext;
        this.namespace = namespace;
        this.key = key;
        this.requestHash = requestHash;
        this.routeKey = routeKey;
        this.ownerToken = ownerToken;
        this.mode = mode;
        this.processingTimeout = processingTimeout;
        this.idempotencyWindow = idempotencyWindow;
        this.windowPolicy = windowPolicy;
        this.recordRetentionTtl = recordRetentionTtl;
        this.recoveryMode = recoveryMode;
        this.now = now;
    }

    public IdempotencyStorageContext getStorageContext() { return storageContext; }
    public String getNamespace() { return namespace; }
    public String getKey() { return key; }
    public String getRequestHash() { return requestHash; }
    public String getRouteKey() { return routeKey; }
    public String getOwnerToken() { return ownerToken; }
    public IdempotencyMode getMode() { return mode; }
    public Duration getProcessingTimeout() { return processingTimeout; }
    public Duration getIdempotencyWindow() { return idempotencyWindow; }
    public IdempotencyWindowPolicy getWindowPolicy() { return windowPolicy; }
    public Duration getRecordRetentionTtl() { return recordRetentionTtl; }
    public IdempotencyRecoveryMode getRecoveryMode() { return recoveryMode; }
    public Instant getNow() { return now; }

    /** 命名参数构造入口。与原构造器保持相同语义，不隐式生成 owner、时间或策略。 */
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private IdempotencyStorageContext storageContext;
        private String namespace;
        private String key;
        private String requestHash;
        private String routeKey;
        private String ownerToken;
        private IdempotencyMode mode;
        private Duration processingTimeout;
        private Duration idempotencyWindow;
        private IdempotencyWindowPolicy windowPolicy;
        private Duration recordRetentionTtl;
        private IdempotencyRecoveryMode recoveryMode;
        private Instant now;

        private Builder() { }

        public Builder storageContext(IdempotencyStorageContext storageContext) { this.storageContext = storageContext; return this; }
        public Builder namespace(String namespace) { this.namespace = namespace; return this; }
        public Builder key(String key) { this.key = key; return this; }
        public Builder requestHash(String requestHash) { this.requestHash = requestHash; return this; }
        public Builder routeKey(String routeKey) { this.routeKey = routeKey; return this; }
        public Builder ownerToken(String ownerToken) { this.ownerToken = ownerToken; return this; }
        public Builder mode(IdempotencyMode mode) { this.mode = mode; return this; }
        public Builder processingTimeout(Duration processingTimeout) { this.processingTimeout = processingTimeout; return this; }
        public Builder idempotencyWindow(Duration idempotencyWindow) { this.idempotencyWindow = idempotencyWindow; return this; }
        public Builder windowPolicy(IdempotencyWindowPolicy windowPolicy) { this.windowPolicy = windowPolicy; return this; }
        public Builder recordRetentionTtl(Duration recordRetentionTtl) { this.recordRetentionTtl = recordRetentionTtl; return this; }
        public Builder recoveryMode(IdempotencyRecoveryMode recoveryMode) { this.recoveryMode = recoveryMode; return this; }
        public Builder now(Instant now) { this.now = now; return this; }

        public IdempotencyAcquireRequest build() {
            return new IdempotencyAcquireRequest(storageContext, namespace, key, requestHash, routeKey, ownerToken, mode,
                    processingTimeout, idempotencyWindow, windowPolicy, recordRetentionTtl, recoveryMode, now);
        }
    }
}
