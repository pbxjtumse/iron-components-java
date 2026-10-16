package com.xjtu.iron.cache.provider.redis.event;


import com.xjtu.iron.cache.core.event.CacheEvent;
import com.xjtu.iron.cache.core.event.CacheEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import java.nio.charset.StandardCharsets;

/**
 * Redis 缓存事件订阅器。
 *
 * <p>监听 Redis Pub/Sub 消息，并交给 CacheEventHandler 处理。</p>
 */
public class RedisCacheEventSubscriber implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(RedisCacheEventSubscriber.class);

    private final RedisCacheEventSerializer serializer;

    private final CacheEventHandler eventHandler;

    public RedisCacheEventSubscriber(
            RedisCacheEventSerializer serializer,
            CacheEventHandler eventHandler
    ) {
        this.serializer = serializer;
        this.eventHandler = eventHandler;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            log.info("[CACHE-EVENT-SUBSCRIBE] body={}", body);
            CacheEvent event = serializer.deserialize(body);
            eventHandler.handle(event);
        } catch (Exception ex) {
            //反序列化失败：记录 error
            //handle 失败：记录 error
            //handle 失败：记录 error
            log.error("[CACHE-EVENT-SUBSCRIBE-ERROR] body={}", body, ex);
        }
    }
}
