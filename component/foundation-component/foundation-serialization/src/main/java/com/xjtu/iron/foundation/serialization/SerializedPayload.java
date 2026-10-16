package com.xjtu.iron.foundation.serialization;

import java.util.Arrays;

/**
 * 序列化后的载荷和元数据。
 */
public final class SerializedPayload {

    private final byte[] body;
    private final SerializationContext context;

    public SerializedPayload(byte[] body, SerializationContext context) {
        this.body = body == null ? new byte[0] : Arrays.copyOf(body, body.length);
        this.context = context == null ? SerializationContext.builder().build() : context;
    }

    public byte[] getBody() { return Arrays.copyOf(body, body.length); }

    public SerializationContext getContext() { return context; }

    public int size() { return body.length; }
}
