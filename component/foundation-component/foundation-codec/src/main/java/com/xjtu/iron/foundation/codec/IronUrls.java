package com.xjtu.iron.foundation.codec;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * URL 编解码工具，统一使用 UTF-8。
 */
public final class IronUrls {

    private IronUrls() {}

    public static String encode(String value) {
        return value == null ? null : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static String decode(String value) {
        return value == null ? null : URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
