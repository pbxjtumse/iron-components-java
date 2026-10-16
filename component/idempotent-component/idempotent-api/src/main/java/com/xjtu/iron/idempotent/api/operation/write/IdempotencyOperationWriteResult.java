package com.xjtu.iron.idempotent.api.operation.write;

import com.xjtu.iron.idempotent.api.repository.IdempotencyRecord;

/** 低层终态写入结果。 */
public final class IdempotencyOperationWriteResult {

    private final IdempotencyOperationWriteStatus status;
    private final IdempotencyRecord record;
    private final Throwable error;

    private IdempotencyOperationWriteResult(IdempotencyOperationWriteStatus status, IdempotencyRecord record, Throwable error) {
        this.status = status;
        this.record = record;
        this.error = error;
    }

    public static IdempotencyOperationWriteResult of(IdempotencyOperationWriteStatus status, IdempotencyRecord record) {
        return new IdempotencyOperationWriteResult(status, record, null);
    }

    public static IdempotencyOperationWriteResult storageError(Throwable error) {
        return new IdempotencyOperationWriteResult(IdempotencyOperationWriteStatus.STORAGE_ERROR, null, error);
    }

    public IdempotencyOperationWriteStatus getStatus() { return status; }
    public IdempotencyRecord getRecord() { return record; }
    public Throwable getError() { return error; }
}
