package com.xjtu.iron.idempotent.starter.hash;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.xjtu.iron.idempotent.api.spi.IdempotencyRequestHasher;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Canonical JSON + SHA-256 的默认 requestHash 实现。
 *
 * <p>调用方应传入“业务指纹 DTO”，不要直接把完整 HTTP 请求对象塞进来。</p>
 */
public final class JacksonSha256IdempotencyRequestHasher implements IdempotencyRequestHasher {

    private final ObjectMapper mapper;

    public JacksonSha256IdempotencyRequestHasher(ObjectMapper mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        this.mapper = mapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @Override
    public String hash(Object businessFingerprint) {
        Objects.requireNonNull(businessFingerprint, "businessFingerprint must not be null");
        try {
            byte[] canonicalJson = mapper.writeValueAsString(businessFingerprint)
                    .getBytes(StandardCharsets.UTF_8);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonicalJson));
        } catch (Exception error) {
            throw new IllegalStateException("calculate idempotency requestHash failed", error);
        }
    }
}
