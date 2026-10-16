package com.xjtu.iron.message.core.consume.idempotency;

import com.xjtu.iron.message.api.consume.context.ConsumeContext;
import com.xjtu.iron.message.api.consume.definition.MessageIdempotencyOptions;
import com.xjtu.iron.message.api.model.MessageEnvelope;

import java.time.Instant;
import java.util.Objects;

/**
 * 传给幂等组件的消息消费幂等上下文。
 */
public final class MessageIdempotencyContext {
    private final String namespace;
    private final String scene;
    private final String idempotencyKey;
    private final long shardKey;
    private final String ownerToken;
    private final String storeName;
    private final int maxAttempts;
    private final Instant processingExpireTime;
    private final Instant expireTime;
    private final MessageEnvelope<?> message;
    private final ConsumeContext consumeContext;
    private final MessageIdempotencyOptions options;

    public MessageIdempotencyContext(
            String namespace,
            String scene,
            String idempotencyKey,
            long shardKey,
            String ownerToken,
            String storeName,
            int maxAttempts,
            Instant processingExpireTime,
            Instant expireTime,
            MessageEnvelope<?> message,
            ConsumeContext consumeContext,
            MessageIdempotencyOptions options) {
        this.namespace = requireText(namespace, "namespace must not be blank");
        this.scene = requireText(scene, "scene must not be blank");
        this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey must not be blank");
        this.shardKey = shardKey;
        this.ownerToken = requireText(ownerToken, "ownerToken must not be blank");
        this.storeName = requireText(storeName, "storeName must not be blank");
        this.maxAttempts = Math.max(1, maxAttempts);
        this.processingExpireTime = Objects.requireNonNull(processingExpireTime, "processingExpireTime must not be null");
        this.expireTime = Objects.requireNonNull(expireTime, "expireTime must not be null");
        this.message = Objects.requireNonNull(message, "message must not be null");
        this.consumeContext = Objects.requireNonNull(consumeContext, "consumeContext must not be null");
        this.options = Objects.requireNonNull(options, "options must not be null");
    }

    public String namespace() { return namespace; }
    public String scene() { return scene; }
    public String idempotencyKey() { return idempotencyKey; }
    public long shardKey() { return shardKey; }
    public String ownerToken() { return ownerToken; }
    public String storeName() { return storeName; }
    public int maxAttempts() { return maxAttempts; }
    public Instant processingExpireTime() { return processingExpireTime; }
    public Instant expireTime() { return expireTime; }
    public MessageEnvelope<?> message() { return message; }
    public ConsumeContext consumeContext() { return consumeContext; }
    public MessageIdempotencyOptions options() { return options; }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
