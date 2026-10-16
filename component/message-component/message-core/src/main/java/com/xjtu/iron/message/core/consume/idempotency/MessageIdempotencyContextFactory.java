package com.xjtu.iron.message.core.consume.idempotency;

import com.xjtu.iron.message.api.consume.context.ConsumeContext;
import com.xjtu.iron.message.api.consume.definition.MessageIdempotencyOptions;
import com.xjtu.iron.message.api.model.MessageEnvelope;

/**
 * 创建消费幂等上下文。
 *
 * <p>只负责根据消息、消费上下文和幂等配置生成幂等执行所需的数据，
 * 不参与状态流转。</p>
 */
public interface MessageIdempotencyContextFactory {

    MessageIdempotencyContext create(
            MessageEnvelope<?> message,
            ConsumeContext context,
            MessageIdempotencyOptions options);
}
