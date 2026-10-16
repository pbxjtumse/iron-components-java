package com.xjtu.iron.foundation.resource;

import java.util.Objects;

/**
 * 资源位置值对象。
 */
public final class ResourceLocation {

    private final String value;

    private ResourceLocation(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("resource location must not be blank");
        }
        this.value = value;
    }

    public static ResourceLocation of(String value) { return new ResourceLocation(value); }

    public String value() { return value; }

    public boolean isClasspath() { return value.startsWith("classpath:"); }

    public boolean isFile() { return value.startsWith("file:"); }

    @Override public String toString() { return value; }

    @Override public boolean equals(Object other) {
        return other instanceof ResourceLocation that && Objects.equals(value, that.value);
    }

    @Override public int hashCode() { return value.hashCode(); }
}
