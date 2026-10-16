package com.xjtu.iron.message.api.publish;

import com.xjtu.iron.message.api.model.MessageDestination;
import com.xjtu.iron.message.api.model.MessageEnvelope;

import java.util.concurrent.CompletionStage;

/** 面向业务代码的统一普通消息发布接口。 */
public interface MessagePublisher {

    /** 使用默认选项同步发送。 */
    default SendResult send(MessageDestination destination, MessageEnvelope<?> message) {
        return send(destination, message, SendOptions.defaults());
    }

    /** 同步发送并等待标准结果。 */
    SendResult send(MessageDestination destination, MessageEnvelope<?> message, SendOptions options);

    /** 使用默认选项异步发送。 */
    default CompletionStage<SendResult> sendAsync(
            MessageDestination destination,
            MessageEnvelope<?> message) {
        return sendAsync(destination, message, SendOptions.defaults());
    }

    /** 异步发送并返回标准结果阶段。 */
    CompletionStage<SendResult> sendAsync(
            MessageDestination destination,
            MessageEnvelope<?> message,
            SendOptions options);
}
