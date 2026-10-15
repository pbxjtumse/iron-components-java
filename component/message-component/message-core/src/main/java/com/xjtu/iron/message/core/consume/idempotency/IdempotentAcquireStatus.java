package com.xjtu.iron.message.core.consume.idempotency;

/**
 * 幂等组件 acquire 的标准结果语义。
 */
public enum IdempotentAcquireStatus {

    /** 当前消息已取得幂等执行权，可以调用消费者。 */
    ACQUIRED,

    /** 消息历史上已成功消费，本次不再重复执行。 */
    DUPLICATE_SUCCESS,

    /** 消息历史上已被丢弃，本次不再重复执行。 */
    DUPLICATE_DISCARDED,

    /** 同一消息正由其他执行者处理。 */
    PROCESSING,

    /** 幂等策略拒绝了当前消费请求。 */
    REJECTED,

    /** 幂等存储访问失败，无法安全判定是否可执行。 */
    STORAGE_ERROR
}
