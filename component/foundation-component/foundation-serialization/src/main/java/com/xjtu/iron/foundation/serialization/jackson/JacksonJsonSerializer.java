package com.xjtu.iron.foundation.serialization.jackson;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjtu.iron.foundation.serialization.*;

import java.util.Objects;

/**
 * Jackson JSON Serializer 实现。
 *
 * <p>构造时会 copy 传入的 ObjectMapper，避免 Spring MVC 或业务模块后续修改全局 Mapper 时，
 * 静默影响消息线协议、缓存载荷或 Outbox 记录。</p>
 */
public final class JacksonJsonSerializer implements Serializer {

    private final ObjectMapper objectMapper;

    public JacksonJsonSerializer() {
        this(JacksonObjectMapperFactory.createDefault());
    }

    public JacksonJsonSerializer(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null").copy();
    }

    @Override
    public SerializedPayload serialize(Object value) {
        return serialize(value, SerializationOptions.builder().build(), SerializationContext.builder().build());
    }

    @Override
    public SerializedPayload serialize(Object value, SerializationOptions options, SerializationContext context) {
        Objects.requireNonNull(options, "options must not be null");
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(value);
            if (bytes.length > options.getMaxBytes()) {
                throw new SerializationException(SerializationOperation.SERIALIZE,
                        "serialized payload exceeds maxBytes: " + options.getMaxBytes(), null);
            }
            return new SerializedPayload(bytes, context);
        } catch (SerializationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SerializationException(SerializationOperation.SERIALIZE, "jackson serialize failed", ex);
        }
    }

    @Override
    public <T> T deserialize(SerializedPayload payload, Class<T> targetType) {
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(targetType, "targetType must not be null");
        try {
            return objectMapper.readValue(payload.getBody(), targetType);
        } catch (Exception ex) {
            throw new SerializationException(SerializationOperation.DESERIALIZE, "jackson deserialize failed", ex);
        }
    }

    @Override
    public <T> T deserialize(SerializedPayload payload, TypeReference<T> targetType) {
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(targetType, "targetType must not be null");
        try {
            JavaType javaType = objectMapper.getTypeFactory().constructType(targetType.getType());
            return objectMapper.readValue(payload.getBody(), javaType);
        } catch (Exception ex) {
            throw new SerializationException(SerializationOperation.DESERIALIZE, "jackson deserialize generic type failed", ex);
        }
    }
}
