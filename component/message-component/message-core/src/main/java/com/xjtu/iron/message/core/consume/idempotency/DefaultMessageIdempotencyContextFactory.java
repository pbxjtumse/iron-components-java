package com.xjtu.iron.message.core.consume.idempotency;

import com.xjtu.iron.message.api.consume.context.ConsumeContext;
import com.xjtu.iron.message.api.consume.definition.MessageIdempotencyOptions;
import com.xjtu.iron.message.api.model.MessageEnvelope;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.zip.CRC32;

/**
 * 默认幂等上下文工厂。
 */
public final class DefaultMessageIdempotencyContextFactory implements MessageIdempotencyContextFactory {

    private final MessageIdempotencySceneResolver sceneResolver;
    private final MessageIdempotencyKeyResolver keyResolver;
    private final MessageIdempotencyOwnerTokenGenerator ownerTokenGenerator;
    private final Clock clock;

    public DefaultMessageIdempotencyContextFactory() {
        this(
                new DefaultMessageIdempotencySceneResolver(),
                new DefaultMessageIdempotencyKeyResolver(),
                new MessageIdempotencyOwnerTokenGenerator(),
                Clock.systemUTC());
    }

    public DefaultMessageIdempotencyContextFactory(
            MessageIdempotencySceneResolver sceneResolver,
            MessageIdempotencyKeyResolver keyResolver,
            MessageIdempotencyOwnerTokenGenerator ownerTokenGenerator,
            Clock clock) {
        this.sceneResolver = Objects.requireNonNull(sceneResolver);
        this.keyResolver = Objects.requireNonNull(keyResolver);
        this.ownerTokenGenerator = Objects.requireNonNull(ownerTokenGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public MessageIdempotencyContext create(
            MessageEnvelope<?> message,
            ConsumeContext context,
            MessageIdempotencyOptions options) {
        String scene = sceneResolver.resolve(message, context, options);
        String key = keyResolver.resolve(message, context, options);
        Instant now = clock.instant();
        return new MessageIdempotencyContext(
                options.namespace(),
                scene,
                key,
                positiveCrc32(options.namespace() + ':' + scene + ':' + key),
                ownerTokenGenerator.nextToken(),
                options.storeName(),
                options.maxAttempts(),
                now.plus(options.processingTimeout()),
                now.plus(options.recordRetention()),
                message,
                context.withIdempotency(scene, key, options.mode()),
                options);
    }

    private static long positiveCrc32(String value) {
        CRC32 crc32 = new CRC32();
        crc32.update(value.getBytes(StandardCharsets.UTF_8));
        return crc32.getValue() & 0x7fffffffL;
    }
}
