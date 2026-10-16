package com.xjtu.iron.idempotent.api.operation.acquire;

import com.xjtu.iron.idempotent.api.repository.IdempotencyRecord;

/** 低层 acquire 结果。 */
public final class IdempotencyOperationAcquireResult {

    private final IdempotencyOperationAcquireStatus status;
    private final IdempotencyRecord record;
    private final Throwable error;

    private IdempotencyOperationAcquireResult(IdempotencyOperationAcquireStatus status, IdempotencyRecord record, Throwable error) {
        this.status = status;
        this.record = record;
        this.error = error;
    }

    public static IdempotencyOperationAcquireResult of(IdempotencyOperationAcquireStatus status, IdempotencyRecord record) {
        return new IdempotencyOperationAcquireResult(status, record, null);
    }

    public static IdempotencyOperationAcquireResult storageError(Throwable error) {
        return new IdempotencyOperationAcquireResult(IdempotencyOperationAcquireStatus.STORAGE_ERROR, null, error);
    }

    public IdempotencyOperationAcquireStatus getStatus() { return status; }
    public IdempotencyRecord getRecord() { return record; }
    public Throwable getError() { return error; }
}
