package com.xjtu.iron.idempotent.api.operation.write;

/** 技术组件调用低层终态写入后的稳定语义。 */
public enum IdempotencyOperationWriteStatus {
    UPDATED,
    STALE_OWNER,
    ALREADY_FINAL,
    NOT_FOUND,
    STORAGE_ERROR
}
