package com.xjtu.iron.foundation.codec;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 内容指纹值对象，默认基于 SHA-256 十六进制摘要。
 */
public final class ContentFingerprint {

    private final String algorithm;
    private final String value;

    public ContentFingerprint(String algorithm, String value) {
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm must not be null");
        this.value = Objects.requireNonNull(value, "value must not be null");
    }

    public static ContentFingerprint sha256(byte[] bytes) {
        return new ContentFingerprint("SHA-256", IronDigests.sha256Hex(bytes));
    }

    public static ContentFingerprint sha256Utf8(String value) {
        return sha256(value == null ? null : value.getBytes(StandardCharsets.UTF_8));
    }

    public String getAlgorithm() { return algorithm; }

    public String getValue() { return value; }

    @Override
    public String toString() { return algorithm + ":" + value; }
}
