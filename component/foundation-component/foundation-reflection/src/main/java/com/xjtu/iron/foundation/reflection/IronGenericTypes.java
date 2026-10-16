package com.xjtu.iron.foundation.reflection;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

/**
 * 泛型类型工具，主要服务序列化和 SPI 扩展点。
 */
public final class IronGenericTypes {

    private IronGenericTypes() {}

    public static Optional<Type> firstActualTypeArgument(Type type) {
        if (type instanceof ParameterizedType parameterizedType && parameterizedType.getActualTypeArguments().length > 0) {
            return Optional.of(parameterizedType.getActualTypeArguments()[0]);
        }
        return Optional.empty();
    }
}
