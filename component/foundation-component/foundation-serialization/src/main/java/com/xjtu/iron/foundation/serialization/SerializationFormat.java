package com.xjtu.iron.foundation.serialization;

/**
 * 序列化格式。
 */
public enum SerializationFormat {
    JSON("application/json"),
    BINARY("application/octet-stream");

    private final String contentType;

    SerializationFormat(String contentType) {
        this.contentType = contentType;
    }

    public String getContentType() {
        return contentType;
    }
}
