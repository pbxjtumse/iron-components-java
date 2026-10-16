package com.xjtu.iron.foundation.context;

import java.util.Objects;

/**
 * 类型安全上下文键。
 *
 * @param <T> 上下文值类型
 */
public final class ContextKey<T> {

    private final String name;
    private final Class<T> type;

    private ContextKey(String name, Class<T> type) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("context key name must not be blank");
        }
        this.name = name;
        this.type = Objects.requireNonNull(type, "type must not be null");
    }

    public static <T> ContextKey<T> of(String name, Class<T> type) {
        return new ContextKey<>(name, type);
    }

    public String getName() { return name; }

    public Class<T> getType() { return type; }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof ContextKey<?> that)) { return false; }
        return name.equals(that.name);
    }

    @Override
    public int hashCode() { return name.hashCode(); }

    @Override
    public String toString() { return name; }
}
