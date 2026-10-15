package com.xjtu.iron.idempotent.api.operation.write;

/** 技术组件调用低层终态写入后的稳定语义。 */
public enum IdempotencyOperationWriteStatus {

    /** 状态和相关结果已按预期更新。 */
    UPDATED,

    /** owner token 或 generation 已过期，本次写入被拒绝。 */
    STALE_OWNER,

    /** 记录已处于终态，不允许再次改写。 */
    ALREADY_FINAL,

    /** 没有找到待更新的幂等记录。 */
    NOT_FOUND,

    /** 底层存储访问失败，未能确认写入结果。 */
    STORAGE_ERROR
}
