package com.xjtu.iron.cache.provider.redis.event;


import com.xjtu.iron.cache.core.event.CacheEvent;
import com.xjtu.iron.cache.core.event.CacheEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 基于 Redis Pub/Sub 的缓存事件发布器。
 *
 * <p>二期第一版用于广播本地缓存失效事件。</p>
 */
public class RedisCacheEventPublisher implements CacheEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(RedisCacheEventPublisher.class);

    private final StringRedisTemplate stringRedisTemplate;

    private final RedisCacheEventSerializer serializer;

    private final String channel;

    public RedisCacheEventPublisher(
            StringRedisTemplate stringRedisTemplate,
            RedisCacheEventSerializer serializer,
            String channel
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.serializer = serializer;
        this.channel = channel;
    }

    @Override
    public void publish(CacheEvent event) {
        String message = serializer.serialize(event);
        log.info("[CACHE-EVENT-PUBLISH] channel={}, eventVersion={}, eventType={}, fullKey={}, sourceInstanceId={}, traceId={}, spanId={}",
                channel,
                event.getEventVersion(),
                event.getEventType(),
                event.getFullKey(),
                event.getSourceInstanceId(),
                event.getTraceId(),
                event.getSpanId());
        stringRedisTemplate.convertAndSend(channel, message);
    }
}
