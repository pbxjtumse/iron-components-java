package com.xjtu.iron.message.core.consume.idempotency;

import java.util.Objects;

/**
 * 默认幂等状态管理实现。
 */
public final class DefaultMessageIdempotencyStateManager implements MessageIdempotencyStateManager {

    private final MessageIdempotentOperations operations;

    public DefaultMessageIdempotencyStateManager(MessageIdempotentOperations operations) {
        this.operations = Objects.requireNonNull(operations);
    }

    @Override
    public IdempotentAcquireResult acquire(MessageIdempotencyContext context) {
        return operations.acquire(context);
    }

    @Override
    public void markSuccess(MessageIdempotencyContext context) {
        operations.markSuccess(context, "SUCCESS", null);
    }

    @Override
    public void markDiscarded(MessageIdempotencyContext context) {
        operations.markDiscarded(context, "DISCARDED", null);
    }

    @Override
    public void markFailed(MessageIdempotencyContext context, Throwable throwable) {
        operations.markFailed(
                context,
                "CONSUME_FAILED",
                throwable == null ? null : throwable.getMessage(),
                throwable == null ? null : throwable.getClass().getName());
    }
}
