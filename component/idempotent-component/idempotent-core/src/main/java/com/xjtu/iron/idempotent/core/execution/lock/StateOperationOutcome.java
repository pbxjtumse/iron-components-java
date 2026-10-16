package com.xjtu.iron.idempotent.core.execution.lock;

/** 状态操作结果及锁层附加信息。不是数据库状态，也不表示已获得业务执行权。 */
public final class StateOperationOutcome<R> {
    private final R result;
    private final boolean lockFallback;
    private final boolean lockRejected;
    private final Throwable error;

    private StateOperationOutcome(R result, boolean lockFallback, boolean lockRejected, Throwable error) {
        this.result = result;
        this.lockFallback = lockFallback;
        this.lockRejected = lockRejected;
        this.error = error;
    }

    static <R> StateOperationOutcome<R> direct(R result) { return new StateOperationOutcome<>(result, false, false, null); }
    static <R> StateOperationOutcome<R> fallback(R result) { return new StateOperationOutcome<>(result, true, false, null); }
    static <R> StateOperationOutcome<R> lockRejected(Throwable error) { return new StateOperationOutcome<>(null, false, true, error); }

    public R result() { return result; }
    public boolean lockFallback() { return lockFallback; }
    public boolean lockRejected() { return lockRejected; }
    public Throwable error() { return error; }
}
