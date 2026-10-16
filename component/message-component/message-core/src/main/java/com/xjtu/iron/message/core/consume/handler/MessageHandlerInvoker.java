package com.xjtu.iron.message.core.consume.handler;

import com.xjtu.iron.message.api.consume.context.ConsumeContext;
import com.xjtu.iron.message.api.consume.decision.ConsumeDecision;
import com.xjtu.iron.message.api.consume.handler.MessageHandler;
import com.xjtu.iron.message.api.model.MessageEnvelope;
import com.xjtu.iron.message.core.consume.ConsumeExceptionClassifier;

import java.util.Objects;

/**
 * 统一业务 Handler 调用入口。
 *
 * <p>隔离业务调用、异常捕获和消费决策转换。</p>
 */
public final class MessageHandlerInvoker {

    private final ConsumeExceptionClassifier classifier;

    public MessageHandlerInvoker(ConsumeExceptionClassifier classifier) {
        this.classifier = Objects.requireNonNull(classifier);
    }

    public <T> ConsumeDecision invoke(
            MessageEnvelope<T> message,
            ConsumeContext context,
            MessageHandler<T> handler) {
        try {
            ConsumeDecision decision = handler.handle(message, context);
            return decision == null ? ConsumeDecision.RETRY : decision;
        } catch (RuntimeException exception) {
            return classifier.classify(exception, context);
        }
    }
}
