package com.xjtu.iron.foundation.serialization;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 序列化上下文，保存 contentType、schemaVersion 和技术属性。
 */
public final class SerializationContext {

    private final String contentType;
    private final String schemaVersion;
    private final Map<String, String> attributes;

    private SerializationContext(Builder builder) {
        this.contentType = builder.contentType;
        this.schemaVersion = builder.schemaVersion;
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(builder.attributes));
    }

    public static Builder builder() { return new Builder(); }

    public String getContentType() { return contentType; }
    public String getSchemaVersion() { return schemaVersion; }
    public Map<String, String> getAttributes() { return attributes; }

    public static final class Builder {
        private String contentType = SerializationFormat.JSON.getContentType();
        private String schemaVersion;
        private final Map<String, String> attributes = new LinkedHashMap<>();

        public Builder contentType(String contentType) { this.contentType = contentType; return this; }
        public Builder schemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; return this; }
        public Builder attribute(String name, String value) { if (name != null && value != null) { attributes.put(name, value); } return this; }
        public SerializationContext build() { return new SerializationContext(this); }
    }
}
