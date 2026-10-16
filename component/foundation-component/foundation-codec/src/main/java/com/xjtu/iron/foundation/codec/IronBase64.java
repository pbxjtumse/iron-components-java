package com.xjtu.iron.foundation.codec;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Base64 编解码工具，优先使用 JDK 标准实现。
 */
public final class IronBase64 {

    private IronBase64() {}

    public static String encodeToString(byte[] bytes) {
        return bytes == null ? null : Base64.getEncoder().encodeToString(bytes);
    }

    public static byte[] decode(String value) {
        return value == null ? null : Base64.getDecoder().decode(value);
    }

    public static String encodeUtf8(String value) {
        return value == null ? null : encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String decodeUtf8(String value) {
        byte[] decoded = decode(value);
        return decoded == null ? null : new String(decoded, StandardCharsets.UTF_8);
    }

    public static String encodeUrlSafe(byte[] bytes) {
        return bytes == null ? null : Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
