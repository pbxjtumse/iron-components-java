package com.xjtu.iron.idempotent.api.execution;

import com.xjtu.iron.idempotent.api.repository.IdempotencyRecord;

import java.util.Objects;
import java.util.Optional;

/**
 * 一次 {@link IdempotencyExecutor} 调用的统一结果。
 *
 * <p>设计上与 distributed-lock 的 {@code LockResult} 保持相似风格：</p>
 * <ul>
 *     <li>{@code status} 表示调用最终结果；</li>
 *     <li>{@code stage} 表示结果产生在哪个阶段；</li>
 *     <li>{@code record} 保存当时的幂等状态快照；</li>
 *     <li>{@code lockFallback} 表示可选分布式锁失败后是否退化为 Repository 原子抢占。</li>
 * </ul>
 *
 * @param <T> 业务结果类型
 */
public final class IdempotencyResult<T> {

    /** 本次调用最终状态，区别于数据库里的持久状态。 */
    private final IdempotencyResultStatus status;

    /** 结果产生的阶段，用于定位是在校验、抢占、执行业务、完成状态还是回放时结束。 */
    private final IdempotencyStage stage;

    /** 业务返回值；只有 EXECUTED / RECOVERED / REPLAYED 且策略可回放时通常非空。 */
    private final T value;

    /** 调用结束时观察到的幂等记录快照。 */
    private final IdempotencyRecord record;

    /** 本次调用关联异常，可能是业务异常、事务异常或 Provider 异常。 */
    private final Throwable error;

    /** true 表示短锁不可用/失败后按配置退化为 Repository 原子状态判断。 */
    private final boolean lockFallback;

    /** true 表示本次 ACQUIRED/RECOVERY_ACQUIRED 执行启用了 Tx-B 事务闭环。 */
    private final boolean transactionApplied;

    private IdempotencyResult(Builder<T> builder) {
        this.status = Objects.requireNonNull(builder.status, "status must not be null");
        this.stage = Objects.requireNonNull(builder.stage, "stage must not be null");
        this.value = builder.value;
        this.record = builder.record;
        this.error = builder.error;
        this.lockFallback = builder.lockFallback;
        this.transactionApplied = builder.transactionApplied;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * EXECUTED / RECOVERED / REPLAYED 都属于调用方可直接使用的成功结果。
     */
    public boolean isSuccess() {
        return status == IdempotencyResultStatus.EXECUTED
                || status == IdempotencyResultStatus.RECOVERED
                || status == IdempotencyResultStatus.REPLAYED;
    }

    public IdempotencyResultStatus status() { return status; }

    public IdempotencyResultStatus getStatus() { return status; }

    public IdempotencyStage stage() { return stage; }

    public IdempotencyStage getStage() { return stage; }

    public Optional<T> value() {
        return Optional.ofNullable(value);
    }

    public T getValue() { return value; }

    public Optional<IdempotencyRecord> record() {
        return Optional.ofNullable(record);
    }

    public IdempotencyRecord getRecord() { return record; }

    public Optional<Throwable> error() {
        return Optional.ofNullable(error);
    }

    public Throwable getError() { return error; }

    public boolean lockFallback() { return lockFallback; }

    public boolean isLockFallback() { return lockFallback; }

    /**
     * 是否真正启用了“Business + markSuccess”同一个本地事务的 Tx-B 闭环。
     *
     * <p>注意：如果 REQUIRED 加入了调用方更外层事务，本次 execute 返回时外层事务仍可能尚未最终提交。
     * 该字段只表示本次工作参与了事务边界，不表示一定已经发生独立物理 COMMIT。</p>
     */
    public boolean isTransactionApplied() {
        return transactionApplied;
    }

    public boolean transactionApplied() { return transactionApplied; }

    public static final class Builder<T> {
        /** 本次调用最终状态。 */
        private IdempotencyResultStatus status;

        /** 结果产生阶段。 */
        private IdempotencyStage stage;

        /** 业务返回值。 */
        private T value;

        /** 幂等记录快照。 */
        private IdempotencyRecord record;

        /** 关联异常。 */
        private Throwable error;

        /** 是否发生短锁失败后的 Repository fallback。 */
        private boolean lockFallback;

        /** 是否启用了 Tx-B 事务闭环。 */
        private boolean transactionApplied;

        public Builder<T> status(IdempotencyResultStatus value) { this.status = value; return this; }

        public Builder<T> stage(IdempotencyStage value) { this.stage = value; return this; }

        public Builder<T> value(T value) { this.value = value; return this; }

        public Builder<T> record(IdempotencyRecord value) { this.record = value; return this; }

        public Builder<T> error(Throwable value) { this.error = value; return this; }

        public Builder<T> lockFallback(boolean value) { this.lockFallback = value; return this; }

        public Builder<T> transactionApplied(boolean value) { this.transactionApplied = value; return this; }

        public IdempotencyResult<T> build() { return new IdempotencyResult<>(this); }
    }
}
