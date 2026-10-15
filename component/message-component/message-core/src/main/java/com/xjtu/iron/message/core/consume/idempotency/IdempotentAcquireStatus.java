package com.xjtu.iron.message.core.consume.idempotency;

/**
 * 幂等组件 acquire 的标准结果语义。
 */
public enum IdempotentAcquireStatus {
    ACQUIRED,
    DUPLICATE_SUCCESS,
    DUPLICATE_DISCARDED,
    PROCESSING,
    REJECTED,
    STORAGE_ERROR
}
