package com.xjtu.iron.idempotent.api.operation.acquire;

/** 技术组件调用低层 acquire 后看到的稳定语义。 */
public enum IdempotencyOperationAcquireStatus {
    ACQUIRED,
    DUPLICATE_SUCCESS,
    DUPLICATE_DISCARDED,
    PROCESSING,
    PROCESSING_EXPIRED,
    FAILED_RETRYABLE,
    FAILED_FINAL,
    KEY_CONFLICT,
    STORAGE_ERROR
}
