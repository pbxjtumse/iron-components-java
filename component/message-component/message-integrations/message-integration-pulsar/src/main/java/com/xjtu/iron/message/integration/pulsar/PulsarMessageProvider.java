package com.xjtu.iron.message.integration.pulsar;

import com.xjtu.iron.message.api.consume.decision.ConsumeDecision;
import com.xjtu.iron.message.api.publish.SendFailureType;
import com.xjtu.iron.message.api.publish.SendStatus;
import com.xjtu.iron.message.integration.pulsar.config.PulsarMessageProviderConfig;
import com.xjtu.iron.message.integration.pulsar.config.PulsarMetadataKeys;
import com.xjtu.iron.message.spi.*;
import org.apache.pulsar.client.api.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
/**
 * Pulsar Provider 实现，负责把统一消息 SPI 映射到 Pulsar Client 的 Producer 和 Consumer。
 *
 * <p>Pulsar 发送成功时会返回 MessageId，Provider 将其作为 providerMessageId 带回 core。
 * 发送超时、连接异常、权限异常等需要被映射为统一的 {@code ProviderSendResult}，由可靠发送层继续决定是否重试。</p>
 *
 * <p>消费侧使用 subscriptionName 对应 consumerGroup，并根据业务返回的 ConsumeDecision 决定 ack 或 negativeAck。</p>
 */
public final class PulsarMessageProvider implements MessageProvider {

    /** Provider 稳定名称。 */
    public static final String NAME = "pulsar";

    /** Pulsar 配置。 */
    private final PulsarMessageProviderConfig config;

    /** 可复用 PulsarClient。 */
    private final PulsarClient client;

    /** 按物理 Topic 缓存线程安全 Producer。 */
    private final ConcurrentMap<String, Producer<byte[]>> producers =
            new ConcurrentHashMap<>();

    /** 当前 Provider 创建的全部 Consumer。 */
    private final ConcurrentMap<String, Consumer<byte[]>> consumers =
            new ConcurrentHashMap<>();

    /** Provider 关闭状态。 */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * 创建 Pulsar Provider。
     *
     * @param config Pulsar 配置
     */
    public PulsarMessageProvider(PulsarMessageProviderConfig config) {
        // 配置不能为空。
        this.config = java.util.Objects.requireNonNull(config, "config must not be null");
        int operationTimeoutMillis = Math.toIntExact(config.operationTimeout().toMillis());
        // 创建 PulsarClient Builder。
        ClientBuilder builder = PulsarClient.builder()
                // 设置服务地址。
                .serviceUrl(config.serviceUrl())
                // 设置操作超时。
                .operationTimeout(operationTimeoutMillis, TimeUnit.MILLISECONDS);
        // Token 存在时启用 Token 认证。
        if (config.authenticationToken() != null) {
            // 使用 Pulsar 官方 AuthenticationFactory 创建认证对象。
            builder.authentication(AuthenticationFactory.token(config.authenticationToken()));
        }
        // 创建 PulsarClient。
        try {
            // 建立共享客户端资源。
            this.client = builder.build();
        } catch (PulsarClientException exception) {
            // 客户端创建失败属于启动错误。
            throw new IllegalStateException("failed to create Pulsar client", exception);
        }
    }

    /**
     * 返回 Provider 名称。
     */
    @Override
    public String name() {
        // 返回稳定小写名称。
        return NAME;
    }

    /**
     * 返回一期公共能力。
     */
    @Override
    public Set<MessageCapability> capabilities() {
        // 声明普通发布和普通消费。
        return Set.of(
                MessageCapability.BASIC_PUBLISH,
                MessageCapability.BASIC_CONSUME,
                MessageCapability.MANUAL_ACK,
                MessageCapability.NEGATIVE_ACK,
                MessageCapability.REDELIVERY,
                MessageCapability.DEAD_LETTER);
    }

    /**
     * 异步发送普通 Pulsar 消息。
     */
    @Override
    public CompletionStage<ProviderSendResult> send(ProviderSendRequest request) {
        // Provider 关闭后返回明确本地失败。
        if (closed.get()) {
            // 不创建或使用已关闭资源。
            return CompletableFuture.completedFuture(ProviderSendResult.failed(
                    SendStatus.FAILED,
                    SendFailureType.CLIENT_ERROR,
                    "Pulsar provider is closed"));
        }
        // 获取或创建当前物理 Topic Producer。
        Producer<byte[]> producer;
        // 捕获懒创建异常。
        try {
            // Producer 是线程安全的，按 Topic 缓存复用。
            producer = producers.computeIfAbsent(
                    request.destination().physicalName(),
                    this::createProducerUnchecked);
        } catch (RuntimeException exception) {
            // 创建失败转换为标准结果。
            return CompletableFuture.completedFuture(classifySendFailure(exception));
        }
        // 创建字节消息 Builder。
        TypedMessageBuilder<byte[]> builder = producer.newMessage()
                // 写入已序列化消息体。
                .value(request.body());
        // messageKey 存在时设置 Pulsar Key；Shared 订阅不据此承诺顺序。
        if (request.messageKey() != null && !request.messageKey().isBlank()) {
            // Shared 订阅不承诺相同 Key 的局部顺序。
            builder.key(request.messageKey());
        }
        // 将完整线级消息头写入 Pulsar Properties。
        request.headers().forEach(builder::property);
        // 创建公共结果 Future。
        CompletableFuture<ProviderSendResult> resultFuture = new CompletableFuture<>();
        // 发起异步发送。
        builder.sendAsync().whenComplete((messageId, throwable) -> {
            // 异常分支执行标准分类。
            if (throwable != null) {
                // 完成非成功结果。
                resultFuture.complete(classifySendFailure(throwable));
                // 结束回调。
                return;
            }
            // Pulsar MessageId 作为原生消息 ID。
            resultFuture.complete(
                    ProviderSendResult.confirmed(messageId.toString()));
        });
        // 返回公共 Future。
        return resultFuture;
    }

    /**
     * 创建 Shared Subscription Consumer。
     */
    @Override
    public ProviderSubscription subscribe(ProviderSubscriptionRequest request) {
        // Provider 关闭后拒绝新订阅。
        if (closed.get()) {
            // 直接抛出启动错误。
            throw new IllegalStateException("Pulsar provider is closed");
        }
        // 创建原生 Consumer。
        Consumer<byte[]> consumer;
        // 捕获订阅创建异常。
        try {
            // 构造一期 Shared Consumer。
            consumer = client.newConsumer(Schema.BYTES)
                    // 订阅物理 Topic。
                    .topic(request.destination().physicalName())
                    // 使用公共 consumerGroup 作为 Subscription Name。
                    .subscriptionName(request.consumerGroup())
                    // Shared 用于同一订阅内负载均衡。
                    .subscriptionType(SubscriptionType.Shared)
                    // 设置接收队列大小。
                    .receiverQueueSize(config.receiverQueueSize())
                    // 设置 Negative ACK 重投延迟。
                    .negativeAckRedeliveryDelay(
                            config.negativeAckRedeliveryDelay().toMillis(),
                            TimeUnit.MILLISECONDS)
                    // 注册异步消息监听器。
                    .messageListener((nativeConsumer, message) -> {
                        // 转换为统一入站消息。
                        ProviderInboundMessage inbound = toInbound(message);
                        // 默认 RETRY 防止异常误确认。
                        ConsumeDecision decision = ConsumeDecision.RETRY;
                        // 调用 core 监听器。
                        try {
                            // 获取业务消费决策。
                            decision = request.listener().onMessage(inbound).decision();
                        } catch (RuntimeException ignored) {
                            // 异常保持 RETRY。
                        }
                        // SUCCESS 时异步 ACK。
                        if (decision == ConsumeDecision.ACK || decision == ConsumeDecision.DISCARD) {
                            // 异步 ACK 失败时主动 Negative ACK，加快至少一次语义下的重新投递。
                            nativeConsumer.acknowledgeAsync(message)
                                    .exceptionally(throwable -> {
                                        // ACK 结果不确定时允许重复，而不能静默丢失消息。
                                        nativeConsumer.negativeAcknowledge(message);
                                        // exceptionally 需要返回 Void。
                                        return null;
                                    });
                            // 结束成功分支。
                            return;
                        }
                        // RETRY 时发送 Negative ACK。
                        nativeConsumer.negativeAcknowledge(message);
                    })
                    // 建立订阅。
                    .subscribe();
        } catch (PulsarClientException exception) {
            // Consumer 创建失败属于启动错误。
            throw new IllegalStateException("failed to create Pulsar consumer", exception);
        }
        // 创建内部 Consumer ID。
        String consumerId = UUID.randomUUID().toString();
        // 保存 Consumer 以支持统一关闭。
        consumers.put(consumerId, consumer);
        // 返回关闭句柄。
        return () -> closeConsumer(consumerId, consumer);
    }

    /**
     * 关闭全部 Consumer、Producer 和 PulsarClient。
     */
    @Override
    public void close() {
        // 只允许第一次关闭释放资源。
        if (closed.compareAndSet(false, true)) {
            // 关闭全部 Consumer。
            consumers.forEach(this::closeConsumer);
            // 清空 Consumer 注册表。
            consumers.clear();
            // 逐个关闭缓存 Producer。
            producers.values().forEach(producer -> {
                // 捕获单个 Producer 关闭异常。
                try {
                    // 释放 Producer 资源。
                    producer.close();
                } catch (PulsarClientException ignored) {
                    // 单个关闭失败不阻断其他资源释放。
                }
            });
            // 清空 Producer 缓存。
            producers.clear();
            // 关闭共享 PulsarClient。
            try {
                // 释放底层连接和线程池。
                client.close();
            } catch (PulsarClientException ignored) {
                // 保持 close 幂等且不中断应用关闭。
            }
        }
    }

    /**
     * 创建物理 Topic Producer，并将受检异常转为运行时异常。
     */
    private Producer<byte[]> createProducerUnchecked(String topic) {
        // 捕获 Producer 创建异常。
        try {
            // 使用 BYTES Schema，业务序列化由 core 完成。
            return client.newProducer(Schema.BYTES)
                    // 设置物理 Topic。
                    .topic(topic)
                    // 创建 Producer。
                    .create();
        } catch (PulsarClientException exception) {
            // computeIfAbsent 不能抛受检异常，因此包装。
            throw new IllegalStateException(
                    "failed to create Pulsar producer for topic " + topic,
                    exception);
        }
    }

    /**
     * 转换 Pulsar Message。
     */
    private static ProviderInboundMessage toInbound(Message<byte[]> message) {
        // 复制 Pulsar Properties。
        Map<String, String> headers = new LinkedHashMap<>(message.getProperties());
        // 仅在消息包含 Key 时读取。
        String messageKey = message.hasKey() ? message.getKey() : null;
        // 构造诊断元数据，保留排查 Topic 路由、原生消息 ID 和重新投递行为所需字段。
        Map<String, String> metadata = new LinkedHashMap<>();
        // 保存 Pulsar 返回的完整 Topic 名称。
        metadata.put(PulsarMetadataKeys.TOPIC, message.getTopicName());
        // 保存 Pulsar 原生消息 ID，便于与 Broker 侧日志和 Topic Stats 对照。
        metadata.put(PulsarMetadataKeys.MESSAGE_ID, message.getMessageId().toString());
        // 保存消息发布时刻。
        metadata.put(
                PulsarMetadataKeys.PUBLISH_TIME,
                Long.toString(message.getPublishTime()));
        // 保存原生重新投递次数；首次投递通常为 0。
        metadata.put(
                PulsarMetadataKeys.REDELIVERY_COUNT,
                Integer.toString(message.getRedeliveryCount()));
        // 业务显式设置 EventTime 时才写入，零值表示消息没有提供 EventTime。
        if (message.getEventTime() > 0L) {
            // 保存业务事件时间。
            metadata.put(
                    PulsarMetadataKeys.EVENT_TIME,
                    Long.toString(message.getEventTime()));
        }
        // 创建统一入站消息。
        return new ProviderInboundMessage(
                message.getMessageId().toString(),
                messageKey,
                headers,
                message.getData(),
                Instant.now(),
                metadata);
    }

    /**
     * 关闭并移除一个 Pulsar Consumer。
     */
    private void closeConsumer(String consumerId, Consumer<byte[]> consumer) {
        // 从注册表移除当前实例。
        consumers.remove(consumerId, consumer);
        // 关闭 Consumer。
        try {
            // 释放订阅和网络资源。
            consumer.close();
        } catch (PulsarClientException ignored) {
            // 单个关闭失败不阻断其他资源释放。
        }
    }

    /**
     * 分类 Pulsar 发送异常。
     */
    private static ProviderSendResult classifySendFailure(Throwable throwable) {
        // 解包异步包装异常和懒创建包装异常。
        Throwable actual = unwrap(throwable);
        // 使用异常简单类名进行跨小版本兼容分类。
        String typeName = actual.getClass().getSimpleName().toLowerCase();
        // 超时无法确认 Broker 是否持久化消息。
        if (typeName.contains("timeout")) {
            // 返回 UNKNOWN。
            return ProviderSendResult.failed(
                    SendStatus.UNKNOWN,
                    SendFailureType.TIMEOUT,
                    actual.getMessage());
        }
        // 授权判断必须先于宽泛的 auth 判断，避免 Authorization 被误分类为 Authentication。
        if (typeName.contains("authorization") || typeName.contains("permission")) {
            // 返回权限错误。
            return ProviderSendResult.failed(
                    SendStatus.REJECTED,
                    SendFailureType.AUTHORIZATION_ERROR,
                    actual.getMessage());
        }
        // 认证失败属于明确拒绝。
        if (typeName.contains("authentication") || typeName.contains("auth")) {
            // 返回认证错误。
            return ProviderSendResult.failed(
                    SendStatus.REJECTED,
                    SendFailureType.AUTHENTICATION_ERROR,
                    actual.getMessage());
        }
        // 连接类异常采用 UNKNOWN，避免无条件重发。
        if (typeName.contains("connect") || typeName.contains("network")) {
            // 返回网络不确定状态。
            return ProviderSendResult.failed(
                    SendStatus.UNKNOWN,
                    SendFailureType.NETWORK_ERROR,
                    actual.getMessage());
        }
        // 其他异常按客户端明确失败处理。
        return ProviderSendResult.failed(
                SendStatus.FAILED,
                SendFailureType.CLIENT_ERROR,
                actual.getMessage());
    }

    /**
     * 解包常见异步和懒创建包装异常。
     */
    private static Throwable unwrap(Throwable throwable) {
        // 从原始异常开始。
        Throwable actual = throwable;
        // 包装异常存在 cause 时持续下钻。
        while (actual.getCause() != null
                && (actual instanceof CompletionException
                || actual instanceof ExecutionException
                || actual instanceof IllegalStateException)) {
            // 切换到下一层 cause。
            actual = actual.getCause();
        }
        // 返回真实异常。
        return actual;
    }
}
