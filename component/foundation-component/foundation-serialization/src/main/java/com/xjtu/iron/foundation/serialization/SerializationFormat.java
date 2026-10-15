package com.xjtu.iron.foundation.serialization;

/**
 * 序列化格式。
 */
public enum SerializationFormat {

    /** JSON 文本格式，对应 {@code application/json}。 */
    JSON("application/json"),

    /** 通用二进制格式，对应 {@code application/octet-stream}。 */
    BINARY("application/octet-stream");

    private final String contentType;

    SerializationFormat(String contentType) {
        this.contentType = contentType;
    }

    public String getContentType() {
        return contentType;
    }
}
