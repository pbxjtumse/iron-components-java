package com.xjtu.iron.foundation.reflection;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * 方法反射工具薄封装。
 */
public final class IronMethods {

    private IronMethods() {}

    public static Optional<Method> findAccessibleMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            Method method = org.apache.commons.lang3.reflect.MethodUtils.getAccessibleMethod(type, name, parameterTypes);
            return Optional.ofNullable(method);
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    public static Object invoke(Object target, String methodName, Object... args) {
        try {
            return org.apache.commons.lang3.reflect.MethodUtils.invokeMethod(target, true, methodName, args);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException("cannot invoke method: " + methodName, ex);
        }
    }
}
