package com.xjtu.iron.message.core.consume;

import com.xjtu.iron.message.api.consume.decision.ConsumeDecision;
import com.xjtu.iron.message.api.consume.decision.ConsumeFailureType;
import com.xjtu.iron.message.api.consume.definition.ConsumerDefinition;
import com.xjtu.iron.message.api.consume.handler.MessageHandler;
import com.xjtu.iron.message.core.codec.MessageWireCodec;
import com.xjtu.iron.message.core.context.CurrentMessage;
import com.xjtu.iron.message.core.context.MessageContextAccessor;
import com.xjtu.iron.message.spi.ProviderConsumeResult;
import com.xjtu.iron.message.spi.ProviderDestination;
import com.xjtu.iron.message.spi.ProviderInboundMessage;

import java.util.Objects;

/**
 * MQ 入站适配层。
 *
 * <p>负责把 ProviderInboundMessage 解码成统一 MessageEnvelope 和 ConsumeContext，
 * 再交给 MessageConsumeExecutor。该类不实现幂等、事务和业务逻辑。</p>
 */
public final class MessageConsumerAdapter {

    private final MessageWireCodec wireCodec;
    private final MessageConsumeExecutor consumeExecutor;
    private final MessageContextAccessor contextAccessor;

    public MessageConsumerAdapter(
            MessageWireCodec wireCodec,
            MessageConsumeExecutor consumeExecutor,
            MessageContextAccessor contextAccessor) {
        this.wireCodec = Objects.requireNonNull(wireCodec, "wireCodec must not be null");
        this.consumeExecutor = Objects.requireNonNull(consumeExecutor, "consumeExecutor must not be null");
        this.contextAccessor = Objects.requireNonNull(contextAccessor, "contextAccessor must not be null");
    }

    public <T> ProviderConsumeResult consume(
            ConsumerDefinition<T> definition,
            MessageHandler<T> handler,
            ProviderDestination destination,
            ProviderInboundMessage inbound) {
        Objects.requireNonNull(definition, "definition must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
        Objects.requireNonNull(destination, "destination must not be null");
        Objects.requireNonNull(inbound, "inbound must not be null");

        MessageWireCodec.DecodedInbound<T> decoded;
        try {
            decoded = wireCodec.decode(definition, destination, inbound);
        } catch (RuntimeException exception) {
            return ProviderConsumeResult.retry(ConsumeFailureType.DECODE_ERROR, exception.getMessage());
        }

        try (MessageContextAccessor.Scope ignored = contextAccessor.open(
                new CurrentMessage(decoded.envelope(), decoded.consumeContext()))) {
            ConsumeDecision decision = consumeExecutor.execute(
                    definition,
                    decoded.envelope(),
                    decoded.consumeContext(),
                    handler);
            return ProviderConsumeResult.of(decision);
        } catch (RuntimeException exception) {
            return ProviderConsumeResult.retry(ConsumeFailureType.HANDLER_ERROR, exception.getMessage());
        }
    }
}
