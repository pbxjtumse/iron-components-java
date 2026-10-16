package com.xjtu.iron.cache.core.event;

import com.xjtu.iron.cache.api.key.CacheKey;

import java.time.Instant;
import java.util.UUID;

/**
 * 缓存事件。
 *
 * <p>用于缓存组件内部的事件通知。</p>
 *
 * <p>二期主要用于 Redis Pub/Sub 广播本地缓存失效事件。</p>
 */
public class CacheEvent {

    /**
     * 当前事件结构版本。
     *
     * <p>用于未来事件结构升级。</p>
     *
     * <p>注意使用 Integer 而不是 int，是为了兼容老版本事件没有 eventVersion 字段的情况。</p>
     */
    private Integer eventVersion = 1;

    /**
     * 事件唯一 ID。
     */
    private String eventId;

    /**
     * 事件类型。
     */
    private CacheEventType eventType;

    /**
     * 缓存名称。
     */
    private String cacheName;

    /**
     * 命名空间。
     */
    private String namespace;

    /**
     * 业务 key。
     */
    private String bizKey;

    /**
     * key 版本。
     */
    private String version;

    /**
     * 完整逻辑 key。
     */
    private String fullKey;

    /**
     * 事件来源应用。
     */
    private String sourceApp;

    /**
     * 事件来源实例 ID。
     */
    private String sourceInstanceId;

    /**
     * 事件产生时间。
     */
    private long timestamp;

    /**
     * 事件原因。
     */
    private String reason;

    /**
     * 链路追踪 traceId。
     *
     * <p>如果当前请求上下文没有 traceId，可以为空。</p>
     */
    private String traceId;

    /**
     * 链路追踪 spanId。
     *
     * <p>如果当前请求上下文没有 spanId，可以为空。</p>
     */
    private String spanId;

    public static CacheEvent evictKey(
            CacheKey key,
            String sourceApp,
            String sourceInstanceId,
            String reason
    ) {
        return evictKey(key, sourceApp, sourceInstanceId, reason, null, null);
    }

    public static CacheEvent evictKey(
            CacheKey key,
            String sourceApp,
            String sourceInstanceId,
            String reason,
            String traceId,
            String spanId
    ) {
        CacheEvent event = new CacheEvent();
        event.setEventVersion(1);
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType(CacheEventType.EVICT_KEY);
        event.setCacheName(key.cacheName());
        event.setNamespace(key.namespace());
        event.setBizKey(key.bizKey());
        event.setVersion(key.version());
        event.setFullKey(key.fullKey());
        event.setSourceApp(sourceApp);
        event.setSourceInstanceId(sourceInstanceId);
        event.setTimestamp(Instant.now().toEpochMilli());
        event.setReason(reason);
        event.setTraceId(traceId);
        event.setSpanId(spanId);
        return event;
    }

    public CacheKey toCacheKey() {
        return CacheKey.of(cacheName, namespace, bizKey, version);
    }

    public Integer getEventVersion() {
        return eventVersion == null ? 1 : eventVersion;
    }

    public void setEventVersion(Integer eventVersion) {
        this.eventVersion = eventVersion;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public CacheEventType getEventType() {
        return eventType;
    }

    public void setEventType(CacheEventType eventType) {
        this.eventType = eventType;
    }

    public String getCacheName() {
        return cacheName;
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getBizKey() {
        return bizKey;
    }

    public void setBizKey(String bizKey) {
        this.bizKey = bizKey;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getFullKey() {
        return fullKey;
    }

    public void setFullKey(String fullKey) {
        this.fullKey = fullKey;
    }

    public String getSourceApp() {
        return sourceApp;
    }

    public void setSourceApp(String sourceApp) {
        this.sourceApp = sourceApp;
    }

    public String getSourceInstanceId() {
        return sourceInstanceId;
    }

    public void setSourceInstanceId(String sourceInstanceId) {
        this.sourceInstanceId = sourceInstanceId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public void setSpanId(String spanId) {
        this.spanId = spanId;
    }
}