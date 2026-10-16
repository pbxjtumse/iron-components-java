package com.xjtu.iron.foundation.resource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;

/**
 * 内存资源实现，主要用于测试或运行时生成的小型资源。
 */
public final class InMemoryResource implements Resource {

    private final String location;
    private final byte[] bytes;

    public InMemoryResource(String location, byte[] bytes) {
        this.location = location;
        this.bytes = bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
    }

    @Override public String getLocation() { return location; }

    @Override public boolean exists() { return true; }

    @Override public InputStream openStream() { return new ByteArrayInputStream(bytes); }
}
