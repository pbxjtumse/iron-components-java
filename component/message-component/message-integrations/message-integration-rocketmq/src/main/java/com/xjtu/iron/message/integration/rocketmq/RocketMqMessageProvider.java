package com.xjtu.iron.message.integration.rocketmq;

import com.xjtu.iron.message.api.consume.decision.ConsumeDecision;
import com.xjtu.iron.message.api.publish.SendFailureType;
import com.xjtu.iron.message.api.publish.SendStatus;
import com.xjtu.iron.message.integration.rocketmq.config.RocketMqMessageProviderConfig;
import com.xjtu.iron.message.integration.rocketmq.config.RocketMqMetadataKeys;
import com.xjtu.iron.message.spi.*;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
/**
 * RocketMQ4 Remoting Provider 实现。
 *
 * <p>当前组件验证的是 RocketMQ 4.x Remoting 协议，使用 DefaultMQProducer 和 DefaultMQPushConsumer，
 * 不是 RocketMQ 5.x gRPC client。这个 Provider 负责把统一请求转换成 RocketMQ Message，
 * 并把 SEND_OK、FLUSH_DISK_TIMEOUT、FLUSH_SLAVE_TIMEOUT、SLAVE_NOT_AVAILABLE 等原生状态映射为统一结果。</p>
 *
 * <p>RocketMQ 的部分非 SEND_OK 状态并不代表消息一定没有进入 Broker，因此更偏 UNKNOWN，而不是简单 FAILED。</p>
 */
public final class RocketMqMessageProvider implements MessageProvider {

    /** Provider 稳定名称。 */
    public static final String NAME = "rocketmq";

    /** RocketMQ Provider 配置快照。 */
    private final RocketMqMessageProviderConfig config;

    /** 普通消息 Producer。 */
    private final DefaultMQProducer producer;

    /** 当前 Provider 允许发送和订阅的物理 Topic。 */
    private final Set<String> topics;

    /** 当前 Provider 创建的全部 PushConsumer。 */
    private final ConcurrentMap<String, DefaultMQPushConsumer> consumers = new ConcurrentHashMap<>();

    /** Provider 关闭状态。 */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * 创建 RocketMQ Provider。
     *
     * @param config RocketMQ 配置
     */
    public RocketMqMessageProvider(RocketMqMessageProviderConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.topics = config.topics();
        this.producer = createAndStartProducer(config);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Set<MessageCapability> capabilities() {
        return Set.of(
                MessageCapability.BASIC_PUBLISH,
                MessageCapability.BASIC_CONSUME,
                MessageCapability.RETURN_CONSUME_STATUS,
                MessageCapability.REDELIVERY,
                MessageCapability.DEAD_LETTER,
                MessageCapability.BATCH_CONSUME);
    }

    @Override
    public CompletionStage<ProviderSendResult> send(ProviderSendRequest request) {
        if (closed.get()) {
            return CompletableFuture.completedFuture(ProviderSendResult.failed(
                    SendStatus.FAILED,
                    SendFailureType.CLIENT_ERROR,
                    "RocketMQ provider is closed"));
        }
        String topic = request.destination().physicalName();
        if (!topics.contains(topic)) {
            return CompletableFuture.completedFuture(ProviderSendResult.failed(
                    SendStatus.REJECTED,
                    SendFailureType.ROUTING_ERROR,
                    "RocketMQ topic was not declared: " + topic));
        }
        Message message = new Message(topic, request.body());
        if (request.messageKey() != null && !request.messageKey().isBlank()) {
            message.setKeys(request.messageKey());
        }
        for (Map.Entry<String, String> entry : request.headers().entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                message.putUserProperty(entry.getKey(), entry.getValue());
            }
        }
        CompletableFuture<ProviderSendResult> resultFuture = new CompletableFuture<>();
        try {
            producer.send(message, new SendCallback() {
                @Override
                public void onSuccess(org.apache.rocketmq.client.producer.SendResult sendResult) {
                    if (sendResult.getSendStatus() == org.apache.rocketmq.client.producer.SendStatus.SEND_OK) {
                        resultFuture.complete(ProviderSendResult.confirmed(
                                sendResult.getMsgId(),
                                sendMetadata(sendResult)));
                        return;
                    }
                    resultFuture.complete(classifySendStatus(sendResult));
                }

                @Override
                public void onException(Throwable throwable) {
                    resultFuture.complete(classifySendFailure(throwable));
                }
            });
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            resultFuture.complete(ProviderSendResult.failed(
                    SendStatus.UNKNOWN,
                    SendFailureType.INTERRUPTED,
                    "thread interrupted while sending RocketMQ message"));
        } catch (Exception exception) {
            resultFuture.complete(classifySendFailure(exception));
        }
        return resultFuture;
    }

    @Override
    public ProviderSubscription subscribe(ProviderSubscriptionRequest request) {
        if (closed.get()) {
            throw new IllegalStateException("RocketMQ provider is closed");
        }
        String topic = request.destination().physicalName();
        if (!topics.contains(topic)) {
            throw new IllegalArgumentException("RocketMQ topic was not declared: " + topic);
        }
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(request.consumerGroup());
        consumer.setNamesrvAddr(config.nameServer());
        consumer.setVipChannelEnabled(config.vipChannelEnabled());
        consumer.setConsumeFromWhere(toConsumeFromWhere(config.consumeFromWhere()));
        consumer.setInstanceName("iron-message-" + UUID.randomUUID());
        try {
            consumer.subscribe(topic, config.tagExpression());
            consumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
                List<ProviderInboundMessage> inboundMessages = toInboundMessages(messages);
                for (ProviderInboundMessage inbound : inboundMessages) {
                    ConsumeDecision decision = ConsumeDecision.RETRY;
                    try {
                        decision = request.listener().onMessage(inbound).decision();
                    } catch (RuntimeException ignored) {
                        decision = ConsumeDecision.RETRY;
                    }
                    if (decision != ConsumeDecision.ACK && decision != ConsumeDecision.DISCARD) {
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            });
            consumer.start();
        } catch (MQClientException exception) {
            consumer.shutdown();
            throw new IllegalStateException("failed to create RocketMQ consumer", exception);
        }
        String consumerId = UUID.randomUUID().toString();
        consumers.put(consumerId, consumer);
        return () -> closeConsumer(consumerId, consumer);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            consumers.forEach(this::closeConsumer);
            consumers.clear();
            producer.shutdown();
        }
    }

    private static DefaultMQProducer createAndStartProducer(RocketMqMessageProviderConfig config) {
        DefaultMQProducer producer = new DefaultMQProducer(config.producerGroup());
        producer.setNamesrvAddr(config.nameServer());
        producer.setSendMsgTimeout(toMillisInt(config.sendTimeout()));
        producer.setRetryTimesWhenSendFailed(config.retryTimesWhenSendFailed());
        producer.setRetryTimesWhenSendAsyncFailed(config.retryTimesWhenSendAsyncFailed());
        producer.setVipChannelEnabled(config.vipChannelEnabled());
        producer.setInstanceName("iron-message-" + UUID.randomUUID());
        try {
            producer.start();
            return producer;
        } catch (MQClientException exception) {
            throw new IllegalStateException("failed to create RocketMQ producer", exception);
        }
    }

    private static int toMillisInt(java.time.Duration duration) {
        long millis = duration.toMillis();
        if (millis > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) millis;
    }

    private static List<ProviderInboundMessage> toInboundMessages(List<MessageExt> messages) {
        List<ProviderInboundMessage> inboundMessages = new ArrayList<>(messages.size());
        for (MessageExt message : messages) {
            inboundMessages.add(toInbound(message));
        }
        return inboundMessages;
    }

    private static ProviderInboundMessage toInbound(MessageExt message) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (message.getProperties() != null) {
            headers.putAll(message.getProperties());
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put(RocketMqMetadataKeys.TOPIC, safe(message.getTopic()));
        metadata.put(RocketMqMetadataKeys.QUEUE_ID, Integer.toString(message.getQueueId()));
        metadata.put(RocketMqMetadataKeys.QUEUE_OFFSET, Long.toString(message.getQueueOffset()));
        metadata.put(RocketMqMetadataKeys.RECONSUME_TIMES, Integer.toString(message.getReconsumeTimes()));
        byte[] body = message.getBody() == null ? new byte[0] : message.getBody();
        return new ProviderInboundMessage(
                message.getMsgId(),
                message.getKeys(),
                headers,
                body,
                Instant.now(),
                metadata);
    }

    private static Map<String, String> sendMetadata(org.apache.rocketmq.client.producer.SendResult result) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put(RocketMqMetadataKeys.MESSAGE_QUEUE, result.getMessageQueue() == null
                ? ""
                : result.getMessageQueue().toString());
        metadata.put(RocketMqMetadataKeys.QUEUE_OFFSET, Long.toString(result.getQueueOffset()));
        metadata.put(RocketMqMetadataKeys.SEND_STATUS, result.getSendStatus().name());
        return metadata;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static ConsumeFromWhere toConsumeFromWhere(String value) {
        if (value == null || value.isBlank()) {
            return ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET;
        }
        try {
            return ConsumeFromWhere.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET;
        }
    }

    private void closeConsumer(String consumerId, DefaultMQPushConsumer consumer) {
        consumers.remove(consumerId, consumer);
        consumer.shutdown();
    }

    private static ProviderSendResult classifySendStatus(
            org.apache.rocketmq.client.producer.SendResult sendResult) {
        Map<String, String> metadata = sendMetadata(sendResult);
        org.apache.rocketmq.client.producer.SendStatus sendStatus = sendResult.getSendStatus();
        String description = "RocketMQ send status is " + sendStatus;
        if (sendStatus == org.apache.rocketmq.client.producer.SendStatus.FLUSH_DISK_TIMEOUT
                || sendStatus == org.apache.rocketmq.client.producer.SendStatus.FLUSH_SLAVE_TIMEOUT
                || sendStatus == org.apache.rocketmq.client.producer.SendStatus.SLAVE_NOT_AVAILABLE) {
            return ProviderSendResult.failed(
                    SendStatus.UNKNOWN,
                    SendFailureType.UNKNOWN_OUTCOME,
                    description,
                    metadata);
        }
        return ProviderSendResult.failed(
                SendStatus.FAILED,
                SendFailureType.BROKER_REJECTED,
                description,
                metadata);
    }

    private static ProviderSendResult classifySendFailure(Throwable throwable) {
        String typeName = throwable == null
                ? ""
                : throwable.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
        String message = throwable == null ? "unknown RocketMQ failure" : throwable.getMessage();
        Map<String, String> metadata = Map.of(
                "exceptionType", throwable == null ? "" : throwable.getClass().getName());
        // 等待发送确认超时属于 UNKNOWN，不能证明 Broker 没收到。
        if (typeName.contains("timeout") || containsIgnoreCase(message, "timeout")) {
            return ProviderSendResult.failed(
                    SendStatus.UNKNOWN,
                    SendFailureType.TIMEOUT,
                    message,
                    metadata);
        }
        // Remoting / NameServer / 连接失败通常发生在真正写入 Broker 之前，可短重试。
        if (typeName.contains("connect")
                || typeName.contains("remoting")
                || containsIgnoreCase(message, "connect")
                || containsIgnoreCase(message, "remoting")
                || containsIgnoreCase(message, "namesrv")) {
            return ProviderSendResult.failed(
                    SendStatus.FAILED,
                    SendFailureType.NETWORK_ERROR,
                    message,
                    metadata);
        }
        // 认证和 ACL 属于明确拒绝。
        if (typeName.contains("auth") || containsIgnoreCase(message, "acl")) {
            return ProviderSendResult.failed(
                    SendStatus.REJECTED,
                    SendFailureType.AUTHENTICATION_ERROR,
                    message,
                    metadata);
        }
        // 路由、Topic 配置错误不是 retry 可以解决的问题。
        if (containsIgnoreCase(message, "no route") || containsIgnoreCase(message, "topic")) {
            return ProviderSendResult.failed(
                    SendStatus.REJECTED,
                    SendFailureType.ROUTING_ERROR,
                    message,
                    metadata);
        }
        return ProviderSendResult.failed(
                SendStatus.FAILED,
                SendFailureType.CLIENT_ERROR,
                message,
                metadata);
    }

    private static boolean containsIgnoreCase(String value, String part) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT)
                .contains(part.toLowerCase(java.util.Locale.ROOT));
    }
}
