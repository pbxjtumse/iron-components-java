package com.xjtu.iron.idempotent.starter.observation;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjtu.iron.idempotent.api.result.*;

import java.util.Objects;

/**
 * Jackson 驱动的类型安全 SNAPSHOT ResultPolicy 工厂。
 *
 * <p>业务可以使用完整泛型类型：</p>
 * <pre>
 * snapshotFactory.snapshot(new IdempotencyTypeRef&lt;List&lt;OrderResult&gt;&gt;() {})
 * </pre>
 * 不再要求 Executor API 传入 Class&lt;T&gt;。
 */
public final class JacksonIdempotencySnapshotPolicyFactory
        implements IdempotencySnapshotPolicyFactory {

    private final ObjectMapper objectMapper;

    public JacksonIdempotencySnapshotPolicyFactory(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public <T> IdempotencyResultPolicy<T> snapshot(IdempotencyTypeRef<T> typeRef) {
        Objects.requireNonNull(typeRef, "typeRef must not be null");
        JavaType javaType = objectMapper.getTypeFactory().constructType(typeRef.getType());

        return IdempotencyResultPolicies.snapshot(new IdempotencyResultSerializer<>() {
            @Override
            public String serialize(T value) throws Exception {
                return objectMapper.writeValueAsString(value);
            }

            @Override
            public T deserialize(String payload) throws Exception {
                return objectMapper.readValue(payload, javaType);
            }
        });
    }
}
