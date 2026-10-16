package com.xjtu.iron.message.api.consume.definition;

import java.time.Duration;
import java.util.Objects;

/**
 * 单个消费者的消费幂等配置。
 *
 * <p>{@code storeName} 只是存储路由提示，不是表名。真实写入单表、业务独立表、分表或分库分表，
 * 由 idempotent-component 的 storage 层根据 namespace、scene、idempotencyKey 和 shardKey 决定。</p>
 */
public final class MessageIdempotencyOptions {
    private static final Duration DEFAULT_PROCESSING_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration DEFAULT_RECORD_RETENTION = Duration.ofDays(7);

    /** 是否启用 message-component 的消费幂等执行逻辑。 */
    private final boolean enabled;

    /** 幂等 key 生成模式，例如 MESSAGE_ID、BUSINESS_KEY、CUSTOM。 */
    private final MessageIdempotencyMode mode;

    /** 幂等命名空间，消息消费默认使用 message-consume。 */
    private final String namespace;

    /** 业务幂等场景，通常与 consumerGroup 或业务消费场景对应。 */
    private final String scene;

    /** 自定义幂等 key resolver 的 Spring Bean 名称，仅 CUSTOM 或复杂 BUSINESS_KEY 场景使用。 */
    private final String keyResolverBean;

    /** PROCESSING 状态的处理超时时间，超过后允许其他消费者 ownerToken 抢占。 */
    private final Duration processingTimeout;

    /** 幂等记录保留时间，超过后可由清理任务删除。 */
    private final Duration recordRetention;

    /** 最大处理尝试次数，包含第一次正常处理。 */
    private final int maxAttempts;

    /** 超过最大次数或 acquire 被拒绝时的处理策略。 */
    private final MessageIdempotencyFailurePolicy failurePolicy;

    /** 幂等存储路由提示，不是表名；真实表由 idempotent-storage 决定。 */
    private final String storeName;

    public MessageIdempotencyOptions(
            boolean enabled,
            MessageIdempotencyMode mode,
            String namespace,
            String scene,
            String keyResolverBean,
            Duration processingTimeout,
            Duration recordRetention,
            int maxAttempts,
            MessageIdempotencyFailurePolicy failurePolicy,
            String storeName) {
        this.enabled = enabled;
        this.mode = mode == null ? MessageIdempotencyMode.MESSAGE_ID : mode;
        this.namespace = textOrDefault(namespace, "message-consume");
        this.scene = normalize(scene);
        this.keyResolverBean = normalize(keyResolverBean);
        this.processingTimeout = processingTimeout == null ? DEFAULT_PROCESSING_TIMEOUT : processingTimeout;
        this.recordRetention = recordRetention == null ? DEFAULT_RECORD_RETENTION : recordRetention;
        this.maxAttempts = maxAttempts <= 0 ? 3 : maxAttempts;
        this.failurePolicy = failurePolicy == null ? MessageIdempotencyFailurePolicy.RETRY : failurePolicy;
        this.storeName = textOrDefault(storeName, "default");
    }

    public static MessageIdempotencyOptions disabled() {
        return new MessageIdempotencyOptions(
                false,
                MessageIdempotencyMode.NONE,
                "message-consume",
                null,
                null,
                DEFAULT_PROCESSING_TIMEOUT,
                DEFAULT_RECORD_RETENTION,
                3,
                MessageIdempotencyFailurePolicy.RETRY,
                "default");
    }

    public static MessageIdempotencyOptions messageId() {
        return new MessageIdempotencyOptions(
                true,
                MessageIdempotencyMode.MESSAGE_ID,
                "message-consume",
                null,
                null,
                DEFAULT_PROCESSING_TIMEOUT,
                DEFAULT_RECORD_RETENTION,
                3,
                MessageIdempotencyFailurePolicy.RETRY,
                "default");
    }

    public boolean enabled() {
        return enabled && mode != MessageIdempotencyMode.NONE;
    }

    public MessageIdempotencyMode mode() {
        return mode;
    }

    public String namespace() {
        return namespace;
    }

    public String scene() {
        return scene;
    }

    public String keyResolverBean() {
        return keyResolverBean;
    }

    public Duration processingTimeout() {
        return processingTimeout;
    }

    public Duration recordRetention() {
        return recordRetention;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public MessageIdempotencyFailurePolicy failurePolicy() {
        return failurePolicy;
    }

    public String storeName() {
        return storeName;
    }

    private static String textOrDefault(String value, String defaultValue) {
        String normalized = normalize(value);
        return normalized == null ? Objects.requireNonNull(defaultValue, "defaultValue must not be null") : normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
