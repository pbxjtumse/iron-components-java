package com.xjtu.iron.message.core.consume;

import com.xjtu.iron.message.api.consume.context.ConsumeContext;
import com.xjtu.iron.message.api.consume.decision.ConsumeDecision;

/**
 * 将业务异常转换为统一消费决策。
 */
public interface ConsumeExceptionClassifier {
    ConsumeDecision classify(Throwable throwable, ConsumeContext context);
}
