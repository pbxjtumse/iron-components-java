package com.xjtu.iron.relational.api.result;

/**
 * 数据库生成键结果。
 */
public final class GeneratedKey<T> {

    /** 数据库返回的生成键值。 */
    private final T value;

    public GeneratedKey(T value) {
        this.value = value;
    }

    public T value() {
        return value;
    }

    @Override
    public String toString() {
        return "GeneratedKey{" +
                "value=" + value +
                '}';
    }
}
