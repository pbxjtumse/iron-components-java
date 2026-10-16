package com.xjtu.iron.foundation.codec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 摘要工具统一门面。
 *
 * <p>这里提供内容摘要，不提供密码哈希、HMAC、数字签名或密钥管理。</p>
 */
public final class IronDigests {

    private IronDigests() {}

    public static String sha256Hex(String value) {
        return value == null ? null : sha256Hex(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Hex(byte[] bytes) {
        return digestHex("SHA-256", bytes);
    }

    public static String sha512Hex(byte[] bytes) {
        return digestHex("SHA-512", bytes);
    }

    public static String digestHex(String algorithm, byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            return IronHex.encode(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalArgumentException("unsupported digest algorithm: " + algorithm, ex);
        }
    }

    public static boolean constantTimeEquals(byte[] left, byte[] right) {
        if (left == null || right == null) {
            return left == right;
        }
        return MessageDigest.isEqual(left, right);
    }
}
