package com.xjtu.iron.storage.routing.api;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;
import java.util.UUID;

/**
 * 带类型的不可变分片值。
 *
 * <p>只接受有稳定文本表示的明确类型，不接受任意业务对象、数组、集合、浮点数或可变 Number。
 * 字符串保留原样（包括空串和首尾空格）；BigDecimal 保留 scale，1.0 与 1.00 是不同的值。
 * 这些规则同时用于值比较与复合键编码，不能在请求间随意改变。</p>
 */
public final class ShardValue {

    /** 类型名参与复合键 v1 编码，重命名或合并类型需要另行规划路由迁移。 */
    public enum Type {
        STRING(String.class), BYTE(Byte.class), SHORT(Short.class), INTEGER(Integer.class), LONG(Long.class),
        BIG_INTEGER(BigInteger.class), DECIMAL(BigDecimal.class), UUID(UUID.class);

        private final Class<?> javaType;

        Type(Class<?> javaType) {
            this.javaType = javaType;
        }
    }

    private final Type type;
    private final Object value;
    private final String canonicalText;

    private ShardValue(Type type, Object value) {
        this.type = type;
        this.value = value;
        this.canonicalText = value.toString();
    }

    /** 仅在 API 边界识别原始值；进入解析链路后始终使用 ShardValue。 */
    public static ShardValue of(Object value) {
        Objects.requireNonNull(value, "shard value must not be null");
        if (value instanceof ShardValue typed) {
            return typed;
        }
        for (Type type : Type.values()) {
            // 使用精确类型，避免可变子类或覆盖 toString 的子类破坏分片稳定性。
            if (value.getClass() == type.javaType) {
                return new ShardValue(type, value);
            }
        }
        throw new StorageRoutingException("Unsupported shard value type: " + value.getClass().getName());
    }

    public Type type() {
        return type;
    }

    public Object value() {
        return value;
    }

    /** 稳定值文本；哈希算法由 Core 决定，模型本身不计算 shard。 */
    public String canonicalText() {
        return canonicalText;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof ShardValue other && type == other.type && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }

    @Override
    public String toString() {
        return "ShardValue{type=" + type + ", value=" + canonicalText + '}';
    }
}
