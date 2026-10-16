package com.xjtu.iron.message.core.consume.idempotency;

import com.xjtu.iron.message.api.consume.context.ConsumeContext;
import com.xjtu.iron.message.api.consume.decision.ConsumeDecision;
import com.xjtu.iron.message.api.consume.definition.MessageIdempotencyOptions;
import com.xjtu.iron.message.api.model.MessageEnvelope;
import com.xjtu.iron.message.core.consume.ConsumeInvocation;

import java.util.Objects;

/**
 * 消费幂等执行入口。
 *
 * <p>本类只负责幂等流程编排，具体上下文创建、状态管理和 acquire 结果处理分别由独立组件承担。</p>
 */
public final class DefaultMessageIdempotencyExecutor implements MessageIdempotencyExecutor {

    private final MessageIdempotencyContextFactory contextFactory;
    private final MessageIdempotencyStateManager stateManager;
    private final MessageIdempotencyDecisionHandler decisionHandler;

    public DefaultMessageIdempotencyExecutor(
            MessageIdempotencyContextFactory contextFactory,
            MessageIdempotencyStateManager stateManager,
            MessageIdempotencyDecisionHandler decisionHandler) {
        this.contextFactory = Objects.requireNonNull(contextFactory);
        this.stateManager = Objects.requireNonNull(stateManager);
        this.decisionHandler = Objects.requireNonNull(decisionHandler);
    }

    @Override
    public ConsumeDecision execute(
            MessageEnvelope<?> message,
            ConsumeContext context,
            MessageIdempotencyOptions options,
            ConsumeInvocation invocation) {
        if (options == null || !options.enabled()) {
            return invocation.invoke();
        }

        MessageIdempotencyContext idempotencyContext =
                contextFactory.create(message, context, options);

        IdempotentAcquireResult acquireResult;
        try {
            acquireResult = stateManager.acquire(idempotencyContext);
        } catch (RuntimeException exception) {
            return ConsumeDecision.RETRY;
        }

        return decisionHandler.handle(
                acquireResult,
                idempotencyContext,
                invocation);
    }
}
