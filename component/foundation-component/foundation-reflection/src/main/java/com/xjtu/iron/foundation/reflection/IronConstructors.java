package com.xjtu.iron.foundation.reflection;

import java.lang.reflect.Constructor;

/**
 * 构造器工具薄封装。
 */
public final class IronConstructors {

    private IronConstructors() {}

    public static <T> T newInstance(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException("cannot create instance of " + type.getName(), ex);
        }
    }

    public static <T> T invokeConstructor(Class<T> type, Object... args) {
        try {
            return org.apache.commons.lang3.reflect.ConstructorUtils.invokeConstructor(type, args);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalArgumentException("cannot invoke constructor of " + type.getName(), ex);
        }
    }
}
