package com.xjtu.iron.foundation.id.nanoid;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Nano ID 字符表与输出长度配置。 */
public final class NanoIdOptions {

    public static final String DEFAULT_ALPHABET =
            "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final int DEFAULT_SIZE = 21;

    /** 允许出现在输出中的唯一字符集合。 */
    private final String alphabet;

    /** 每个 Nano ID 的字符长度。 */
    private final int size;

    private NanoIdOptions(Builder builder) {
        this.alphabet = validateAlphabet(builder.alphabet);
        if (builder.size <= 0) {
            throw new IllegalArgumentException("size must be greater than zero");
        }
        this.size = builder.size;
    }

    public static NanoIdOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getAlphabet() {
        return alphabet;
    }

    public int getSize() {
        return size;
    }

    private static String validateAlphabet(String alphabet) {
        String actualAlphabet = Objects.requireNonNull(
                alphabet,
                "alphabet must not be null"
        );
        if (actualAlphabet.isEmpty() || actualAlphabet.length() > 256) {
            throw new IllegalArgumentException(
                    "alphabet length must be between 1 and 256"
            );
        }

        Set<Character> distinct = new HashSet<>();
        for (int index = 0; index < actualAlphabet.length(); index++) {
            if (!distinct.add(actualAlphabet.charAt(index))) {
                throw new IllegalArgumentException(
                        "alphabet must not contain duplicate characters"
                );
            }
        }
        return actualAlphabet;
    }

    public static final class Builder {

        private String alphabet = DEFAULT_ALPHABET;
        private int size = DEFAULT_SIZE;

        private Builder() {
        }

        public Builder alphabet(String alphabet) {
            this.alphabet = alphabet;
            return this;
        }

        public Builder size(int size) {
            this.size = size;
            return this;
        }

        public NanoIdOptions build() {
            return new NanoIdOptions(this);
        }
    }
}
