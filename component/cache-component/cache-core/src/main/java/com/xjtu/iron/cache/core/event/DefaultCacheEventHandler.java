package com.xjtu.iron.cache.core.event;


import com.xjtu.iron.cache.core.invalidate.LocalCacheInvalidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认缓存事件处理器。
 *
 * <p>二期第一版只处理 EVICT_KEY。</p>
 *
 * <p>收到 EVICT_KEY 后，只删除当前实例的本地缓存。</p>
 */
public class DefaultCacheEventHandler implements CacheEventHandler {

    private static final Logger log = LoggerFactory.getLogger(DefaultCacheEventHandler.class);

    private final LocalCacheInvalidator localCacheInvalidator;

    private final String currentInstanceId;

    public DefaultCacheEventHandler(
            LocalCacheInvalidator localCacheInvalidator,
            String currentInstanceId
    ) {
        this.localCacheInvalidator = localCacheInvalidator;
        this.currentInstanceId = currentInstanceId;
    }

    @Override
    public void handle(CacheEvent event) {
        if (event == null || event.getEventType() == null) {
            return;
        }

        log.info("[CACHE-EVENT-HANDLE] eventVersion={}, eventType={}, fullKey={}, sourceInstanceId={}, currentInstanceId={}, traceId={}, spanId={}",
                event.getEventVersion(),
                event.getEventType(),
                event.getFullKey(),
                event.getSourceInstanceId(),
                currentInstanceId,
                event.getTraceId(),
                event.getSpanId());

        if (currentInstanceId != null
                && currentInstanceId.equals(event.getSourceInstanceId())) {
            log.info("[CACHE-EVENT-HANDLE] ignore self event, fullKey={}", event.getFullKey());
            return;
        }

        if (event.getEventType() == CacheEventType.UNKNOWN) {
            log.warn("[CACHE-EVENT-HANDLE] ignore unknown eventType, fullKey={}, eventId={}",
                    event.getFullKey(),
                    event.getEventId());
            return;
        }

        if (event.getEventType() == CacheEventType.EVICT_KEY) {
            localCacheInvalidator.invalidateLocal(event.toCacheKey());
            return;
        }

        if (event.getEventType() == CacheEventType.CLEAR_CACHE_NAME) {
            localCacheInvalidator.invalidateLocalCacheName(event.getCacheName());
        }
    }
}
