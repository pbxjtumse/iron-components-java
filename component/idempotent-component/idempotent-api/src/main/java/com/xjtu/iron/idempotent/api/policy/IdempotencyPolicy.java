package com.xjtu.iron.idempotent.api.policy;

import com.xjtu.iron.idempotent.api.execution.IdempotencyRequest;
import com.xjtu.iron.idempotent.api.recovery.IdempotencyRecoveryPolicy;

import java.time.Duration;

/**
 * 一类幂等业务的稳定执行策略。
 *
 * <p>{@link IdempotencyRequest} 回答“这一次请求是谁”，本类回答“这一类业务平时应该怎么做幂等”。</p>
 *
 * <p>注意：ResultPolicy 是带业务返回类型的调用级能力，不放进这里；事务是否真正生效，也不是一个静态开关，
 * 而是由 transaction integration 是否存在 + Repository capability 是否支持共同决定。</p>
 */
public final class IdempotencyPolicy {

    public static final String DEFAULT_NAMESPACE = "default";
    public static final Duration DEFAULT_PROCESSING_TIMEOUT = Duration.ofSeconds(30);
    public static final Duration DEFAULT_WINDOW = Duration.ofMinutes(10);
    public static final Duration DEFAULT_RETENTION = Duration.ZERO;

    /** 命名 Policy 的稳定名称，便于业务只引用 policyName。 */
    private final String name;

    /** WINDOWED = 有限时间幂等窗口；DURABLE = 长期业务事实。 */
    private final IdempotencyMode mode;

    /** 幂等隔离域，最终唯一身份通常是 namespace + key。 */
    private final String namespace;

    /** 显式 Repository 名称；为空时由 RepositoryRegistry 按 mode 选择默认 Provider。 */
    private final String repositoryName;

    /** 当前 generation 的执行租约，不是幂等窗口。 */
    private final Duration processingTimeout;

    /** 仅 WINDOWED 使用：同 key 多久仍被视为同一次逻辑请求。 */
    private final Duration idempotencyWindow;

    /** WINDOWED 的固定窗口 / 滑动窗口策略。 */
    private final IdempotencyWindowPolicy windowPolicy;

    /** 语义窗口结束后旧物理记录额外保留多久。 */
    private final Duration recordRetentionTtl;

    /** 哪些异常 generation 允许外部 Reliable Task 接管。 */
    private final IdempotencyRecoveryPolicy recoveryPolicy;

    /** 可选短锁配置，只影响竞争收敛，不改变 Repository 正确性。 */
    private final IdempotencyLockOptions lockOptions;

    private IdempotencyPolicy(Builder builder) {
        this.name = normalize(builder.name);
        this.mode = builder.mode == null ? IdempotencyMode.DURABLE : builder.mode;
        this.namespace = normalize(builder.namespace) == null ? DEFAULT_NAMESPACE : builder.namespace.trim();
        this.repositoryName = normalize(builder.repositoryName);
        this.processingTimeout = builder.processingTimeout == null ? DEFAULT_PROCESSING_TIMEOUT : builder.processingTimeout;
        this.idempotencyWindow = builder.idempotencyWindow != null
                ? builder.idempotencyWindow : (mode.isWindowed() ? DEFAULT_WINDOW : null);
        this.windowPolicy = builder.windowPolicy == null ? IdempotencyWindowPolicy.FIXED_FROM_FIRST_ACQUIRE : builder.windowPolicy;
        this.recordRetentionTtl = builder.recordRetentionTtl == null ? DEFAULT_RETENTION : builder.recordRetentionTtl;
        this.recoveryPolicy = builder.recoveryPolicy != null
                ? builder.recoveryPolicy
                : (mode == IdempotencyMode.DURABLE ? IdempotencyRecoveryPolicy.externalTask() : IdempotencyRecoveryPolicy.none());
        this.lockOptions = builder.lockOptions == null ? IdempotencyLockOptions.disabled() : builder.lockOptions;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static IdempotencyPolicy durable() {
        return builder().mode(IdempotencyMode.DURABLE).build();
    }

    public static IdempotencyPolicy windowed() {
        return builder().mode(IdempotencyMode.WINDOWED).build();
    }

    /**
     * 在真正进入 Repository 抢占前完成静态策略校验。
     */
    public void validate() {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("namespace must not be blank");
        }
        if (processingTimeout == null || processingTimeout.isZero() || processingTimeout.isNegative()) {
            throw new IllegalArgumentException("processingTimeout must be positive");
        }
        if (recordRetentionTtl == null || recordRetentionTtl.isNegative()) {
            throw new IllegalArgumentException("recordRetentionTtl must not be negative");
        }
        if (mode.isWindowed()) {
            if (idempotencyWindow == null || idempotencyWindow.isZero() || idempotencyWindow.isNegative()) {
                throw new IllegalArgumentException("WINDOWED requires positive idempotencyWindow");
            }
            if (idempotencyWindow.compareTo(processingTimeout) <= 0) {
                throw new IllegalArgumentException("idempotencyWindow must be greater than processingTimeout");
            }
        }
        recoveryPolicy.validate();
        lockOptions.validate();
    }

    public String getName() { return name; }
    public IdempotencyMode getMode() { return mode; }
    public String getNamespace() { return namespace; }
    public String getRepositoryName() { return repositoryName; }
    public Duration getProcessingTimeout() { return processingTimeout; }
    public Duration getIdempotencyWindow() { return idempotencyWindow; }
    public IdempotencyWindowPolicy getWindowPolicy() { return windowPolicy; }
    public Duration getRecordRetentionTtl() { return recordRetentionTtl; }
    public IdempotencyRecoveryPolicy getRecoveryPolicy() { return recoveryPolicy; }
    public IdempotencyLockOptions getLockOptions() { return lockOptions; }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static final class Builder {
        private String name;
        private IdempotencyMode mode = IdempotencyMode.DURABLE;
        private String namespace = DEFAULT_NAMESPACE;
        private String repositoryName;
        private Duration processingTimeout = DEFAULT_PROCESSING_TIMEOUT;
        private Duration idempotencyWindow;
        private IdempotencyWindowPolicy windowPolicy = IdempotencyWindowPolicy.FIXED_FROM_FIRST_ACQUIRE;
        private Duration recordRetentionTtl = DEFAULT_RETENTION;
        private IdempotencyRecoveryPolicy recoveryPolicy;
        private IdempotencyLockOptions lockOptions = IdempotencyLockOptions.disabled();

        public Builder name(String value) { this.name = value; return this; }
        public Builder mode(IdempotencyMode value) { this.mode = value; return this; }
        public Builder namespace(String value) { this.namespace = value; return this; }
        public Builder repositoryName(String value) { this.repositoryName = value; return this; }
        public Builder processingTimeout(Duration value) { this.processingTimeout = value; return this; }
        public Builder idempotencyWindow(Duration value) { this.idempotencyWindow = value; return this; }
        public Builder windowPolicy(IdempotencyWindowPolicy value) { this.windowPolicy = value; return this; }
        public Builder recordRetentionTtl(Duration value) { this.recordRetentionTtl = value; return this; }
        public Builder recoveryPolicy(IdempotencyRecoveryPolicy value) { this.recoveryPolicy = value; return this; }
        public Builder lockOptions(IdempotencyLockOptions value) { this.lockOptions = value; return this; }

        public IdempotencyPolicy build() {
            IdempotencyPolicy policy = new IdempotencyPolicy(this);
            policy.validate();
            return policy;
        }
    }
}
