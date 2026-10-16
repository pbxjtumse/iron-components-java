package com.xjtu.iron.foundation.reflection;

import java.lang.reflect.Field;
import java.util.Optional;

/**
 * 字段反射工具薄封装。
 */
public final class IronFields {

    private IronFields() {}

    public static Optional<Field> findField(Class<?> type, String fieldName) {
        Field field = org.apache.commons.lang3.reflect.FieldUtils.getField(type, fieldName, true);
        return Optional.ofNullable(field);
    }

    public static Object readField(Object target, String fieldName) {
        try {
            return org.apache.commons.lang3.reflect.FieldUtils.readField(target, fieldName, true);
        } catch (IllegalAccessException ex) {
            throw new IllegalArgumentException("cannot read field: " + fieldName, ex);
        }
    }
}
