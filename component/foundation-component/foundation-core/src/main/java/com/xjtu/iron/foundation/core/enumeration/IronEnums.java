package com.xjtu.iron.foundation.core.enumeration;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * 枚举解析工具统一门面。
 */
public final class IronEnums {

    private IronEnums() {
    }

    public static <E extends Enum<E>> Optional<E> byName(Class<E> enumType, String name) {
        Objects.requireNonNull(enumType, "enumType must not be null");
        if (name == null) {
            return Optional.empty();
        }
        return Arrays.stream(enumType.getEnumConstants())
                .filter(value -> value.name().equals(name))
                .findFirst();
    }

    public static <E extends Enum<E>> Optional<E> byNameIgnoreCase(Class<E> enumType, String name) {
        Objects.requireNonNull(enumType, "enumType must not be null");
        if (name == null) {
            return Optional.empty();
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(enumType.getEnumConstants())
                .filter(value -> value.name().toUpperCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }

    public static <C, E extends Enum<E> & CodeEnum<C>> Optional<E> byCode(Class<E> enumType, C code) {
        Objects.requireNonNull(enumType, "enumType must not be null");
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(enumType.getEnumConstants())
                .filter(value -> Objects.equals(value.getCode(), code))
                .findFirst();
    }

    public static <E extends Enum<E>> E requireByName(Class<E> enumType, String name, String fieldName) {
        return byName(enumType, name)
                .orElseThrow(() -> new IllegalArgumentException(fieldName + " is not a valid enum name: " + name));
    }
}
