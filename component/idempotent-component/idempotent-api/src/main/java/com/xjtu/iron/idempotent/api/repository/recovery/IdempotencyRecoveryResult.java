package com.xjtu.iron.idempotent.api.repository.recovery;

import com.xjtu.iron.idempotent.api.repository.IdempotencyRecord;

/** recover(...) 对应的 Repository 返回值。 */
public final class IdempotencyRecoveryResult {

    private final IdempotencyRecoveryStatus status;
    private final IdempotencyRecord record;
    private final String recoveryReason;
    private final Throwable error;

    private IdempotencyRecoveryResult(IdempotencyRecoveryStatus status, IdempotencyRecord record, String recoveryReason, Throwable error) {
        this.status = status;
        this.record = record;
        this.recoveryReason = recoveryReason;
        this.error = error;
    }

    public static IdempotencyRecoveryResult of(IdempotencyRecoveryStatus status, IdempotencyRecord record) {
        return new IdempotencyRecoveryResult(status, record, null, null);
    }

    public static IdempotencyRecoveryResult acquired(IdempotencyRecord record, String recoveryReason) {
        return new IdempotencyRecoveryResult(IdempotencyRecoveryStatus.RECOVERY_ACQUIRED, record, recoveryReason, null);
    }

    public static IdempotencyRecoveryResult providerError(Throwable error) {
        return new IdempotencyRecoveryResult(IdempotencyRecoveryStatus.PROVIDER_ERROR, null, null, error);
    }

    public IdempotencyRecoveryStatus getStatus() { return status; }
    public IdempotencyRecord getRecord() { return record; }
    public String getRecoveryReason() { return recoveryReason; }
    public Throwable getError() { return error; }
}
