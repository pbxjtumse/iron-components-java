package com.xjtu.iron.foundation.serialization;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * 泛型类型引用，避免反序列化 List&lt;T&gt;、Map&lt;K,V&gt; 等类型时丢失泛型信息。
 *
 * @param <T> 目标类型
 */
public abstract class TypeReference<T> {

    private final Type type;

    protected TypeReference() {
        Type superType = getClass().getGenericSuperclass();
        if (!(superType instanceof ParameterizedType parameterizedType)) {
            throw new IllegalArgumentException("TypeReference must be created with generic type");
        }
        this.type = parameterizedType.getActualTypeArguments()[0];
    }

    public Type getType() { return type; }
}
