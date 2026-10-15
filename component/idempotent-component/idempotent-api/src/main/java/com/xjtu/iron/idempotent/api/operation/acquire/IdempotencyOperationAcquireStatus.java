package com.xjtu.iron.idempotent.api.operation.acquire;

/** 技术组件调用低层 acquire 后看到的稳定语义。 */
public enum IdempotencyOperationAcquireStatus {

    /** 当前请求已取得执行权，可以执行业务。 */
    ACQUIRED,

    /** 同一幂等请求历史上已成功，应返回或重放成功结果。 */
    DUPLICATE_SUCCESS,

    /** 同一幂等请求历史上已被丢弃，不再执行业务。 */
    DUPLICATE_DISCARDED,

    /** 请求正由其他 owner 处理，当前调用未取得执行权。 */
    PROCESSING,

    /** 记录仍为处理中，但其 lease 已过期，可根据策略接管。 */
    PROCESSING_EXPIRED,

    /** 上一次执行失败，且策略允许重试。 */
    FAILED_RETRYABLE,

    /** 上一次执行失败，且不允许自动重试。 */
    FAILED_FINAL,

    /** 相同幂等 key 对应的请求指纹不一致。 */
    KEY_CONFLICT,

    /** 底层存储访问失败，未能完成抢占判定。 */
    STORAGE_ERROR
}
