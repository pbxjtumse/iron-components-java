package com.xjtu.iron.foundation.reflection;

import java.util.Objects;

/**
 * Class 工具薄封装，底层优先复用 Apache Commons Lang。
 */
public final class IronClasses {

    private IronClasses() {}

    public static Class<?> loadClass(String className, ClassLoader classLoader) {
        try {
            ClassLoader actual = classLoader == null ? Thread.currentThread().getContextClassLoader() : classLoader;
            return Class.forName(className, false, actual);
        } catch (ClassNotFoundException ex) {
            throw new IllegalArgumentException("class not found: " + className, ex);
        }
    }

    public static boolean isAssignable(Class<?> targetType, Class<?> sourceType) {
        return org.apache.commons.lang3.ClassUtils.isAssignable(sourceType, targetType);
    }

    public static String shortName(Class<?> type) {
        Objects.requireNonNull(type, "type must not be null");
        return org.apache.commons.lang3.ClassUtils.getShortClassName(type);
    }
}
