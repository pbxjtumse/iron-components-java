package com.xjtu.iron.cache.provider.redis.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xjtu.iron.cache.api.exception.CacheSerializeException;
import com.xjtu.iron.cache.core.event.CacheEvent;

/**
 * 基于 Jackson 的 Redis 缓存事件序列化器。
 *
 * <p>这里不要直接修改全局 ObjectMapper，而是 copy 一份。</p>
 *
 * <p>这样不会影响业务系统自己的 JSON 配置。</p>
 */
public class JacksonRedisCacheEventSerializer implements RedisCacheEventSerializer {

    /**
     * 事件专用 ObjectMapper。
     */
    private final ObjectMapper objectMapper;

    public JacksonRedisCacheEventSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy();

        /*
         * 兼容未来新增字段：
         * 老版本应用收到新版本事件时，忽略未知字段。
         */
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        /*
         * 兼容未知枚举：
         * 需要配合 @JsonEnumDefaultValue 使用。
         */
        this.objectMapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true);

        /*
         * 枚举大小写宽松匹配。
         */
        this.objectMapper.setConfig(
                this.objectMapper.getSerializationConfig()
                        .with(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
        );
        this.objectMapper.setConfig(
                this.objectMapper.getDeserializationConfig()
                        .with(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
        );

        /*
         * JSON 里不输出 null 字段，降低消息体积。
         */
        this.objectMapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    }

    @Override
    public String serialize(CacheEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception ex) {
            throw new CacheSerializeException("Serialize cache event failed", ex);
        }
    }

    @Override
    public CacheEvent deserialize(String text) {
        try {
            return objectMapper.readValue(text, CacheEvent.class);
        } catch (Exception ex) {
            throw new CacheSerializeException("Deserialize cache event failed", ex);
        }
    }
}
