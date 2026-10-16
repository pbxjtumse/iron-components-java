package com.xjtu.iron.distributed.lock.core.execute;

import com.xjtu.iron.distributed.lock.api.status.LockStage;
import com.xjtu.iron.distributed.lock.api.status.LockStatus;

/** execute callback 的原始执行结果，不包含 release 阶段合并结果。 */
public final class ExecutionOutcome<T> {
    private final T value;
    private final LockStatus status;
    private final LockStage stage;
    private final Throwable error;

    private ExecutionOutcome(T value, LockStatus status, LockStage stage, Throwable error) {
        this.value = value;
        this.status = status;
        this.stage = stage;
        this.error = error;
    }

    public static <T> ExecutionOutcome<T> success(T value) {
        return new ExecutionOutcome<>(value, LockStatus.SUCCESS, LockStage.EXECUTE, null);
    }

    public static <T> ExecutionOutcome<T> failure(LockStatus status, LockStage stage, Throwable error) {
        return new ExecutionOutcome<>(null, status, stage, error);
    }

    public T getValue() { return value; }
    public LockStatus getStatus() { return status; }
    public LockStage getStage() { return stage; }
    public Throwable getError() { return error; }
}
