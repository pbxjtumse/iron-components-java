package com.xjtu.iron.foundation.codec;

import java.util.HexFormat;

/**
 * 十六进制编解码工具，基于 Java 17 HexFormat。
 */
public final class IronHex {

    private static final HexFormat HEX = HexFormat.of();

    private IronHex() {}

    public static String encode(byte[] bytes) {
        return bytes == null ? null : HEX.formatHex(bytes);
    }

    public static byte[] decode(String hex) {
        return hex == null ? null : HEX.parseHex(hex);
    }
}
