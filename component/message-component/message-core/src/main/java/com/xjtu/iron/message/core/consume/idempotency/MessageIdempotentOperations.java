package com.xjtu.iron.message.core.consume.idempotency;

/**
 * message-component 与 idempotent-component 的最小协作接口。
 *
 * <p>真实项目中可以由 idempotent-component 的 adapter 实现该接口，内部再访问 JDBC 单表、业务独立表、分表或分库分表。</p>
 */
public interface MessageIdempotentOperations {
    IdempotentAcquireResult acquire(MessageIdempotencyContext context);
    void markSuccess(MessageIdempotencyContext context, String resultCode, String resultSnapshot);
    void markFailed(MessageIdempotencyContext context, String errorCode, String errorMessage, String errorType);
    void markDiscarded(MessageIdempotencyContext context, String resultCode, String resultSnapshot);
}
