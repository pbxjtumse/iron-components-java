package com.xjtu.iron.message.integration.kafka;

import com.xjtu.iron.message.api.consume.decision.ConsumeDecision;
import com.xjtu.iron.message.api.publish.SendFailureType;
import com.xjtu.iron.message.api.publish.SendStatus;
import com.xjtu.iron.message.integration.kafka.config.KafkaMessageProviderConfig;
import com.xjtu.iron.message.integration.kafka.config.KafkaMetadataKeys;
import com.xjtu.iron.message.spi.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.*;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
/**
 * Kafka Provider 实现，负责把 message-component 的统一 SPI 请求适配到 Kafka Producer/Consumer。
 *
 * <p>发送侧会把 {@code ProviderSendRequest} 转换为 Kafka {@code ProducerRecord}，等待 Kafka 返回 RecordMetadata，
 * 再映射为 {@code ProviderSendResult}。消费侧会把 Kafka ConsumerRecord 还原为 {@code ProviderInboundMessage}，
 * 再交给 core 的 wire codec 解码。</p>
 *
 * <p>二期可靠发送要求 Provider 尽量准确地区分 CONFIRMED、FAILED、REJECTED、UNKNOWN。
 * Kafka 发送确认超时不等价于明确失败，因此应优先映射为 UNKNOWN，避免盲目重试导致重复消息。</p>
 */
public final class KafkaMessageProvider implements MessageProvider {

    /** Provider 稳定名称。 */
    public static final String NAME = "kafka";

    /** Kafka 配置。 */
    private final KafkaMessageProviderConfig config;

    /** 线程安全且可复用的 Kafka Producer。 */
    private final Producer<String, byte[]> producer;

    /** 当前 Provider 创建的全部 Consumer Worker。 */
    private final ConcurrentMap<String, KafkaConsumerWorker> workers = new ConcurrentHashMap<>();

    /** Provider 关闭状态。 */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * 创建 Kafka Provider。
     *
     * <p>{@code config}：Kafka 配置</p>
     */
    public KafkaMessageProvider(KafkaMessageProviderConfig config) {
        // 配置不能为空。
        this.config = java.util.Objects.requireNonNull(config, "config must not be null");
        // 创建 Producer 配置 Map。
        Map<String, Object> properties = new LinkedHashMap<>();
        // 先合并调用方原生配置，后续稳定公共配置拥有最终优先级。
        properties.putAll(config.producerProperties());
        // 写入 Bootstrap Server，禁止原生 Map 悄悄覆盖稳定字段。
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers());
        // Producer 使用稳定前缀作为诊断 client.id。
        properties.put(ProducerConfig.CLIENT_ID_CONFIG, config.clientIdPrefix() + "-producer");
        // messageKey 映射为 Kafka Record Key，因此强制使用 StringSerializer。
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // 强制 value 使用 byte[]，业务序列化已经在 core 完成。
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        // 公共 CONFIRMED 语义要求所有同步副本确认。
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        // 显式开启 Producer 幂等写入，避免依赖 Kafka 版本默认值。
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        // 创建 Kafka Producer。
        this.producer = new KafkaProducer<>(properties);
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
        // Kafka Provider 支持普通发布和普通消费。
        return Set.of(
                MessageCapability.BASIC_PUBLISH,
                MessageCapability.BASIC_CONSUME,
                MessageCapability.OFFSET_COMMIT,
                MessageCapability.REDELIVERY,
                MessageCapability.ORDERED_CONSUME,
                MessageCapability.BATCH_CONSUME);
    }

    /**
     * 异步发送普通 Kafka 记录。
     */
    @Override
    public CompletionStage<ProviderSendResult> send(ProviderSendRequest request) {
        // Provider 关闭后返回明确本地失败。
        if (closed.get()) {
            // 不再调用已关闭 Producer。
            return CompletableFuture.completedFuture(ProviderSendResult.failed(
                    SendStatus.FAILED,
                    SendFailureType.CLIENT_ERROR,
                    "Kafka provider is closed"));
        }
        // 创建 Kafka ProducerRecord。
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(
                request.destination().physicalName(),
                request.messageKey(),
                request.body());
        // 将线级字符串消息头编码为 UTF-8 Kafka Header。
        request.headers().forEach((name, value) ->
                record.headers().add(
                        name,
                        value.getBytes(StandardCharsets.UTF_8)));
        // 创建公共结果 Future。
        CompletableFuture<ProviderSendResult> resultFuture = new CompletableFuture<>();
        // 发起 Kafka 异步发送。
        producer.send(record, (metadata, exception) -> {
            // 异常分支进行保守分类。
            if (exception != null) {
                // 完成非成功结果。
                resultFuture.complete(classifySendFailure(exception));
                // 结束回调。
                return;
            }
            // 组合 Topic、Partition 和 Offset 作为原生位置标识。
            String providerMessageId = metadata.topic()
                    + "-"
                    + metadata.partition()
                    + "@"
                    + metadata.offset();
            // 构造诊断元数据。
            Map<String, String> resultMetadata = Map.of(
                    KafkaMetadataKeys.TOPIC, metadata.topic(),
                    KafkaMetadataKeys.PARTITION, Integer.toString(metadata.partition()),
                    KafkaMetadataKeys.OFFSET, Long.toString(metadata.offset()));
            // Broker 返回 RecordMetadata 后标记明确确认。
            resultFuture.complete(ProviderSendResult.confirmed(
                    providerMessageId,
                    resultMetadata));
        });
        // 返回异步结果。
        return resultFuture;
    }

    /**
     * 创建并启动一个 Kafka Consumer Worker。
     */
    @Override
    public ProviderSubscription subscribe(ProviderSubscriptionRequest request) {
        // Provider 关闭后拒绝创建新消费者。
        if (closed.get()) {
            // 直接抛出启动错误。
            throw new IllegalStateException("Kafka provider is closed");
        }
        // 创建内部 Worker ID。
        String workerId = UUID.randomUUID().toString();
        // 创建 Worker。
        KafkaConsumerWorker worker = new KafkaConsumerWorker(workerId, request);
        // 先登记 Worker，保证并发 close 能发现它。
        workers.put(workerId, worker);
        // 启动专用 poll 线程。
        worker.start();
        // 返回 Worker 作为关闭句柄。
        return worker;
    }

    /**
     * 关闭 Producer 和全部 Consumer Worker。
     */
    @Override
    public void close() {
        // 只允许第一次关闭执行资源释放。
        if (closed.compareAndSet(false, true)) {
            // 通知全部 Worker 停止。
            workers.values().forEach(KafkaConsumerWorker::close);
            // 清空注册表。
            workers.clear();
            // 在有限时间内关闭 Producer。
            producer.close(Duration.ofSeconds(5));
        }
    }

    /**
     * 分类 Kafka 发送异常。
     */
    private static ProviderSendResult classifySendFailure(Exception exception) {
        Map<String, String> metadata = Map.of(
                "exceptionType", exception.getClass().getName());
        // 认证失败属于明确拒绝。
        if (exception instanceof AuthenticationException) {
            return ProviderSendResult.failed(
                    SendStatus.REJECTED,
                    SendFailureType.AUTHENTICATION_ERROR,
                    exception.getMessage(),
                    metadata);
        }
        // 授权失败属于明确拒绝。
        if (exception instanceof AuthorizationException) {
            return ProviderSendResult.failed(
                    SendStatus.REJECTED,
                    SendFailureType.AUTHORIZATION_ERROR,
                    exception.getMessage(),
                    metadata);
        }
        // 序列化异常、非法 Topic、超大记录都不是重试能够修复的问题。
        if (exception instanceof SerializationException) {
            return ProviderSendResult.failed(
                    SendStatus.REJECTED,
                    SendFailureType.SERIALIZATION_ERROR,
                    exception.getMessage(),
                    metadata);
        }
        if (exception instanceof InvalidTopicException) {
            return ProviderSendResult.failed(
                    SendStatus.REJECTED,
                    SendFailureType.ROUTING_ERROR,
                    exception.getMessage(),
                    metadata);
        }
        if (exception instanceof RecordTooLargeException) {
            return ProviderSendResult.failed(
                    SendStatus.REJECTED,
                    SendFailureType.BROKER_REJECTED,
                    exception.getMessage(),
                    metadata);
        }
        // Kafka TimeoutException 很可能已经进入发送流程但没有拿到确认，保守标记 UNKNOWN。
        if (exception instanceof TimeoutException) {
            return ProviderSendResult.failed(
                    SendStatus.UNKNOWN,
                    SendFailureType.TIMEOUT,
                    exception.getMessage(),
                    metadata);
        }
        // 连接类、网络类可重试异常按明确临时失败处理，让可靠发送层触发 retry。
        if (exception instanceof NetworkException || exception instanceof RetriableException) {
            return ProviderSendResult.failed(
                    SendStatus.FAILED,
                    SendFailureType.NETWORK_ERROR,
                    exception.getMessage(),
                    metadata);
        }
        // 其他异常按明确客户端失败处理。
        return ProviderSendResult.failed(
                SendStatus.FAILED,
                SendFailureType.CLIENT_ERROR,
                exception.getMessage(),
                metadata);
    }

    /**
     * 将 Kafka Headers 转换为统一字符串 Map。
     */
    private static Map<String, String> toHeaders(ConsumerRecord<String, byte[]> record) {
        // 使用有序 Map，重复 Header 保留最后一个值。
        Map<String, String> headers = new LinkedHashMap<>();
        // 遍历 Kafka Header。
        for (Header header : record.headers()) {
            // null 字节值按空字符串处理。
            String value = header.value() == null
                    ? ""
                    : new String(header.value(), StandardCharsets.UTF_8);
            // 写入统一字符串消息头。
            headers.put(header.key(), value);
        }
        // 返回可由 ProviderInboundMessage 防御复制的 Map。
        return headers;
    }

    /**
     * 每个业务订阅对应一个 KafkaConsumer 和专用 poll 线程。
     */
    private final class KafkaConsumerWorker implements ProviderSubscription, Runnable {

        /** Worker ID。 */
        private final String workerId;

        /** Provider 订阅请求。 */
        private final ProviderSubscriptionRequest request;

        /** 只能由 Worker 线程使用的 KafkaConsumer。 */
        private final KafkaConsumer<String, byte[]> consumer;

        /** 专用 poll 线程。 */
        private final Thread thread;

        /** Worker 运行状态。 */
        private final AtomicBoolean running = new AtomicBoolean(true);

        /**
         * 创建 Kafka Consumer Worker。
         */
        private KafkaConsumerWorker(String workerId, ProviderSubscriptionRequest request) {
            // 保存 Worker ID。
            this.workerId = workerId;
            // 保存订阅请求。
            this.request = request;
            // 创建 Consumer 配置。
            Map<String, Object> properties = new LinkedHashMap<>();
            // 先合并调用方原生配置，稳定公共配置在后面拥有最终优先级。
            properties.putAll(config.consumerProperties());
            // 写入 Broker 地址。
            properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.bootstrapServers());
            // 写入业务消费组。
            properties.put(ConsumerConfig.GROUP_ID_CONFIG, request.consumerGroup());
            // 为每个 Worker 生成独立 clientId。
            properties.put(
                    ConsumerConfig.CLIENT_ID_CONFIG,
                    config.clientIdPrefix() + "-consumer-" + workerId);
            // Kafka Record Key 还原为统一 messageKey，因此强制使用 StringDeserializer。
            properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            // 强制 value 保持 byte[] 交给 core 反序列化。
            properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
            // 禁止自动提交，确保业务成功后再推进 offset。
            properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
            // 未找到历史位点时默认从最早开始，生产环境可通过原生配置覆盖。
            properties.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            // 创建 KafkaConsumer。
            this.consumer = new KafkaConsumer<>(properties);
            // 创建守护线程。
            this.thread = new Thread(this, "iron-message-kafka-" + request.consumerGroup() + "-" + workerId);
            // 守护线程不阻止 JVM 正常退出。
            this.thread.setDaemon(true);
        }

        /**
         * 启动 Worker。
         */
        private void start() {
            // 启动专用线程。
            thread.start();
        }

        /**
         * 执行 Kafka poll 循环。
         */
        @Override
        public void run() {
            // KafkaConsumer 必须在使用它的线程中订阅 Topic。
            consumer.subscribe(List.of(request.destination().physicalName()));
            // 捕获 wakeup 关闭信号。
            try {
                // 持续拉取直到关闭。
                while (running.get()) {
                    // 拉取一批消息。
                    ConsumerRecords<String, byte[]> records = consumer.poll(config.pollTimeout());
                    // 当前 Worker 只订阅一个物理 Topic；本轮 poll 可能同时返回该 Topic 的多个分区。
                    // 按分区分别处理，某分区失败不能让其他分区已拉取记录被静默越过。
                    for (TopicPartition partition : records.partitions()) {
                        // 当前分区内保持 offset 顺序。
                        for (ConsumerRecord<String, byte[]> record : records.records(partition)) {
                            // 关闭后不再处理新消息。
                            if (!running.get()) {
                                // 跳出当前分区。
                                break;
                            }
                            // 构造 Provider 入站元数据。
                            Map<String, String> metadata = Map.of(
                                    KafkaMetadataKeys.TOPIC, record.topic(),
                                    KafkaMetadataKeys.PARTITION, Integer.toString(record.partition()),
                                    KafkaMetadataKeys.OFFSET, Long.toString(record.offset()),
                                    KafkaMetadataKeys.TIMESTAMP, Long.toString(record.timestamp()));
                            // 构造统一 Provider 入站消息。
                            ProviderInboundMessage inbound = new ProviderInboundMessage(
                                    record.topic() + "-" + record.partition() + "@" + record.offset(),
                                    record.key(),
                                    toHeaders(record),
                                    record.value(),
                                    Instant.now(),
                                    metadata);
                            // 默认 RETRY，防止监听器异常误提交 offset。
                            ConsumeDecision decision = ConsumeDecision.RETRY;
                            // 调用 core 监听器。
                            try {
                                // 获取业务消费决策。
                                decision = request.listener().onMessage(inbound).decision();
                            } catch (RuntimeException ignored) {
                                // 异常保持 RETRY。
                            }
                            // SUCCESS 时提交当前分区下一条 offset。
                            if (decision == ConsumeDecision.ACK || decision == ConsumeDecision.DISCARD) {
                                // Kafka 提交语义是下一条待消费 offset。
                                OffsetAndMetadata nextOffset = new OffsetAndMetadata(record.offset() + 1);
                                // 只提交当前分区，避免覆盖其他分区未完成的进度。
                                consumer.commitSync(Map.of(partition, nextOffset));
                                // 继续处理当前分区下一条记录。
                                continue;
                            }
                            // RETRY 时仅回退当前分区到失败记录。
                            consumer.seek(partition, record.offset());
                            // 固定退避避免高速空转。
                            sleep(config.consumerRetryBackoff());
                            // 当前分区不能越过失败记录，但其他分区仍可继续处理。
                            break;
                        }
                    }
                }
            } catch (WakeupException exception) {
                // 正常关闭时忽略 WakeupException。
                if (running.get()) {
                    // 非关闭 wakeup 继续抛出，暴露 Consumer Worker 异常退出。
                    throw exception;
                }
            } finally {
                // KafkaConsumer 必须由当前使用线程关闭。
                consumer.close(Duration.ofSeconds(5));
                // 从 Provider Worker 表移除。
                workers.remove(workerId, this);
            }
        }

        /**
         * 停止 Worker。
         */
        @Override
        public void close() {
            // 仅第一次关闭执行 wakeup。
            if (running.compareAndSet(true, false)) {
                // wakeup 用于安全中断阻塞中的 poll。
                consumer.wakeup();
            }
        }

        /**
         * 执行固定退避。
         */
        private void sleep(Duration duration) {
            // 零退避直接返回。
            if (duration.isZero()) {
                // 不执行 Thread.sleep。
                return;
            }
            // 捕获线程中断。
            try {
                // 使用毫秒级退避。
                Thread.sleep(duration.toMillis());
            } catch (InterruptedException exception) {
                // 恢复中断标记。
                Thread.currentThread().interrupt();
                // 请求结束 Worker。
                running.set(false);
            }
        }
    }
}
