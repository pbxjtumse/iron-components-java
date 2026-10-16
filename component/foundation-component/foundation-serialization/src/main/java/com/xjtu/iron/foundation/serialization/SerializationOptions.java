package com.xjtu.iron.foundation.serialization;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 单次序列化选项。
 */
public final class SerializationOptions {

    private final Charset charset;
    private final int maxBytes;

    private SerializationOptions(Builder builder) {
        this.charset = builder.charset;
        this.maxBytes = builder.maxBytes;
    }

    public static Builder builder() { return new Builder(); }

    public Charset getCharset() { return charset; }

    public int getMaxBytes() { return maxBytes; }

    public static final class Builder {
        private Charset charset = StandardCharsets.UTF_8;
        private int maxBytes = 1024 * 1024;

        public Builder charset(Charset charset) { this.charset = charset; return this; }
        public Builder maxBytes(int maxBytes) { this.maxBytes = maxBytes; return this; }
        public SerializationOptions build() {
            if (maxBytes <= 0) { throw new IllegalArgumentException("maxBytes must be positive"); }
            return new SerializationOptions(this);
        }
    }
}
