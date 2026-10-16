package com.xjtu.iron.idempotent.api.recovery;

import java.util.Objects;

/**
 * 幂等异常执行的恢复策略。
 *
 * <p>RecoveryPolicy 只回答“什么状态允许被外部可靠任务接管”，
 * 不负责扫描、调度、线程池、MQ 投递等任务基础设施。</p>
 *
 * <p>该策略集中表达恢复模式、PROCESSING 超时恢复与 retryable FAILED 恢复，
 * 避免后续继续增加多个互相组合困难的 boolean。</p>
 */
public final class IdempotencyRecoveryPolicy {

    /** NONE 表示不允许外部恢复；EXTERNAL_TASK 表示由外部 Reliable Task 显式调用 recover()。 */
    private final IdempotencyRecoveryMode mode;

    /** PROCESSING 执行租约过期后，Reliable Task 是否允许接管。 */
    private final boolean recoverProcessingTimeout;

    /** FAILED 且 failureRetryable=true 时，Reliable Task 是否允许接管。 */
    private final boolean recoverRetryableFailure;

    private IdempotencyRecoveryPolicy(Builder builder) {
        this.mode = builder.mode == null ? IdempotencyRecoveryMode.NONE : builder.mode;
        this.recoverProcessingTimeout = builder.recoverProcessingTimeout;
        this.recoverRetryableFailure = builder.recoverRetryableFailure;
    }

    public static IdempotencyRecoveryPolicy none() {
        return builder().mode(IdempotencyRecoveryMode.NONE).build();
    }

    /**
     * DURABLE 推荐默认值：
     * 外部 Reliable Task 可以接管超时 PROCESSING，也可以恢复 retryable FAILED。
     */
    public static IdempotencyRecoveryPolicy externalTask() {
        return builder()
                .mode(IdempotencyRecoveryMode.EXTERNAL_TASK)
                .recoverProcessingTimeout(true)
                .recoverRetryableFailure(true)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public void validate() {
        Objects.requireNonNull(mode, "recovery mode must not be null");
        if (mode == IdempotencyRecoveryMode.NONE && (recoverProcessingTimeout || recoverRetryableFailure)) {
            throw new IllegalArgumentException("recovery mode NONE cannot enable processing-timeout/failed recovery");
        }
    }

    public IdempotencyRecoveryMode getMode() {
        return mode;
    }

    public boolean isRecoverProcessingTimeout() {
        return recoverProcessingTimeout;
    }

    public boolean isRecoverRetryableFailure() {
        return recoverRetryableFailure;
    }

    public boolean isExternalTaskEnabled() {
        return mode == IdempotencyRecoveryMode.EXTERNAL_TASK;
    }

    public static final class Builder {
        private IdempotencyRecoveryMode mode = IdempotencyRecoveryMode.NONE;
        private boolean recoverProcessingTimeout;
        private boolean recoverRetryableFailure;

        public Builder mode(IdempotencyRecoveryMode value) {
            this.mode = value;
            return this;
        }

        public Builder recoverProcessingTimeout(boolean value) {
            this.recoverProcessingTimeout = value;
            return this;
        }

        public Builder recoverRetryableFailure(boolean value) {
            this.recoverRetryableFailure = value;
            return this;
        }

        public IdempotencyRecoveryPolicy build() {
            IdempotencyRecoveryPolicy policy = new IdempotencyRecoveryPolicy(this);
            policy.validate();
            return policy;
        }
    }
}
