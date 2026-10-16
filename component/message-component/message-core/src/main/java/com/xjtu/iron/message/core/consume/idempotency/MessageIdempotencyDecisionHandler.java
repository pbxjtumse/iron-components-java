package com.xjtu.iron.message.core.consume.idempotency;

import com.xjtu.iron.message.api.consume.decision.ConsumeDecision;
import com.xjtu.iron.message.api.consume.definition.MessageIdempotencyFailurePolicy;
import com.xjtu.iron.message.core.consume.ConsumeInvocation;

import java.util.Objects;

/**
 * 根据 acquire 结果决定后续幂等行为。
 *
 * <p>V4 后不再负责事务。事务属于 MessageConsumeExecutor 的执行策略，
 * 这里仅关注幂等状态推进。</p>
 */
public final class MessageIdempotencyDecisionHandler {

    private final MessageIdempotencyStateManager stateManager;

    public MessageIdempotencyDecisionHandler(
            MessageIdempotencyStateManager stateManager) {
        this.stateManager = Objects.requireNonNull(stateManager, "stateManager must not be null");
    }

    public ConsumeDecision handle(
            IdempotentAcquireResult result,
            MessageIdempotencyContext context,
            ConsumeInvocation invocation) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(invocation, "invocation must not be null");
        return switch (result.status()) {
            case ACQUIRED -> executeAcquired(context, invocation);
            case DUPLICATE_SUCCESS, DUPLICATE_DISCARDED -> ConsumeDecision.ACK;
            case PROCESSING, STORAGE_ERROR -> ConsumeDecision.RETRY;
            case REJECTED -> rejected(context.options().failurePolicy());
        };
    }

    private ConsumeDecision executeAcquired(
            MessageIdempotencyContext context,
            ConsumeInvocation invocation) {
        try {
            ConsumeDecision decision = invocation.invoke();
            if (decision == ConsumeDecision.ACK) {
                stateManager.markSuccess(context);
                return ConsumeDecision.ACK;
            }
            if (decision == ConsumeDecision.DISCARD) {
                stateManager.markDiscarded(context);
                return ConsumeDecision.DISCARD;
            }
            markFailedBestEffort(context, null);
            return decision == ConsumeDecision.DEAD_LETTER
                    ? ConsumeDecision.DEAD_LETTER
                    : ConsumeDecision.RETRY;
        } catch (RuntimeException exception) {
            markFailedBestEffort(context, exception);
            return ConsumeDecision.RETRY;
        }
    }

    private void markFailedBestEffort(
            MessageIdempotencyContext context,
            RuntimeException exception) {
        try {
            stateManager.markFailed(context, exception);
        } catch (RuntimeException ignored) {
            // best effort: 幂等失败标记不能覆盖当前消费决策。
        }
    }

    private ConsumeDecision rejected(MessageIdempotencyFailurePolicy policy) {
        return policy == MessageIdempotencyFailurePolicy.DISCARD
                ? ConsumeDecision.DISCARD
                : policy == MessageIdempotencyFailurePolicy.DEAD_LETTER
                ? ConsumeDecision.DEAD_LETTER
                : ConsumeDecision.RETRY;
    }
}
