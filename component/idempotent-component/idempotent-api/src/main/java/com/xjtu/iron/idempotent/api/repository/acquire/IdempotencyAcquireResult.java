package com.xjtu.iron.idempotent.api.repository.acquire;

import com.xjtu.iron.idempotent.api.repository.IdempotencyRecord;

/** 普通 tryAcquire 的决策结果。 */
public final class IdempotencyAcquireResult {

    /** Repository 派生出的普通抢占状态。 */
    private final IdempotencyAcquireStatus status;

    /** 与状态对应的幂等记录快照；Provider 异常或 NOT_FOUND 类场景可能为空。 */
    private final IdempotencyRecord record;

    /** WINDOWED 窗口结束后是否在旧物理记录上开启了新 generation。 */
    private final boolean windowRollover;

    /** Provider 异常；只有 PROVIDER_ERROR 时通常非空。 */
    private final Throwable error;

    private IdempotencyAcquireResult(IdempotencyAcquireStatus status, IdempotencyRecord record, boolean windowRollover, Throwable error) {
        this.status = status;
        this.record = record;
        this.windowRollover = windowRollover;
        this.error = error;
    }

    public static IdempotencyAcquireResult of(IdempotencyAcquireStatus status, IdempotencyRecord record) {
        return new IdempotencyAcquireResult(status, record, false, null);
    }

    public static IdempotencyAcquireResult acquired(IdempotencyRecord record, boolean windowRollover) {
        return new IdempotencyAcquireResult(IdempotencyAcquireStatus.ACQUIRED, record, windowRollover, null);
    }

    public static IdempotencyAcquireResult providerError(Throwable error) {
        return new IdempotencyAcquireResult(IdempotencyAcquireStatus.PROVIDER_ERROR, null, false, error);
    }

    public IdempotencyAcquireStatus getStatus() { return status; }
    public IdempotencyRecord getRecord() { return record; }

    /** WINDOWED 语义窗口结束后，是否由旧物理记录滚动开启了新 generation。 */
    public boolean isWindowRollover() { return windowRollover; }

    public Throwable getError() { return error; }
}
