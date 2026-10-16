package com.xjtu.iron.cache.core.impl;

import com.xjtu.iron.cache.api.enums.CacheOperation;
import com.xjtu.iron.cache.api.exception.CacheException;
import com.xjtu.iron.cache.api.key.CacheKey;
import com.xjtu.iron.cache.api.model.CacheSpec;
import com.xjtu.iron.cache.api.model.CacheValue;
import com.xjtu.iron.cache.core.CacheProvider;
import com.xjtu.iron.cache.core.event.CacheEvent;
import com.xjtu.iron.cache.core.event.CacheEventPublishFailurePolicy;
import com.xjtu.iron.cache.core.event.CacheEventPublisher;
import com.xjtu.iron.cache.core.trace.CacheTraceContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 验证缓存删除、失效通知数量与事件失败策略。 */
class CacheEvictionTest {

    private final CacheKey key = CacheKey.of("profiles", "test", "user-1");
    private final TrackingProvider provider = new TrackingProvider();
    private final TrackingMetrics metrics = new TrackingMetrics();

    @Test
    void shouldPublishOneTracedEventAfterSuccessfulDeletion() {
        List<CacheEvent> events = new ArrayList<>();
        client(events::add, CacheEventPublishFailurePolicy.IGNORE).evict(key);

        assertEquals(1, provider.deletions);
        assertEquals(1, events.size());
        assertEquals(key.fullKey(), events.get(0).getFullKey());
        assertEquals("trace-1", events.get(0).getTraceId());
        assertEquals("span-1", events.get(0).getSpanId());
        assertEquals("instance-1", events.get(0).getSourceInstanceId());
    }

    @Test
    void ignorePolicyShouldKeepDeletionSuccessfulWhenPublishingFails() {
        RuntimeException failure = new IllegalStateException("publisher unavailable");
        assertDoesNotThrow(() -> client(event -> { throw failure; }, CacheEventPublishFailurePolicy.IGNORE).evict(key));

        assertEquals(1, provider.deletions);
        assertEquals(List.of(CacheOperation.PUBLISH_EVENT), metrics.errors);
    }

    @Test
    void throwPolicyShouldReportPublishingFailureWithItsOriginalCause() {
        RuntimeException failure = new IllegalStateException("publisher unavailable");
        CacheException thrown = assertThrows(CacheException.class,
            () -> client(event -> { throw failure; }, CacheEventPublishFailurePolicy.THROW).evict(key));

        assertSame(failure, thrown.getCause());
        assertTrue(thrown.getMessage().contains("event publish failed"));
        assertEquals(1, provider.deletions);
        assertEquals(List.of(CacheOperation.PUBLISH_EVENT), metrics.errors);
    }

    @Test
    void deletionFailureShouldNotPublishAnInvalidationEvent() {
        RuntimeException failure = new IllegalStateException("cache unavailable");
        provider.failure = failure;
        List<CacheEvent> events = new ArrayList<>();

        CacheException thrown = assertThrows(CacheException.class,
            () -> client(events::add, CacheEventPublishFailurePolicy.IGNORE).evict(key));

        assertSame(failure, thrown.getCause());
        assertTrue(events.isEmpty());
        assertEquals(List.of(CacheOperation.EVICT), metrics.errors);
    }

    private DefaultCacheClient client(CacheEventPublisher publisher, CacheEventPublishFailurePolicy policy) {
        CacheTraceContext trace = new CacheTraceContext() {
            @Override
            public String currentTraceId() { return "trace-1"; }

            @Override
            public String currentSpanId() { return "span-1"; }
        };
        // 本组场景只调用 evict，不需要加载策略与 TTL 计算器。
        return new DefaultCacheClient(provider, null, null, null, metrics, publisher, "test", "instance-1", policy, trace);
    }

    private static final class TrackingMetrics extends NoopCacheMetricsRecorder {
        private final List<CacheOperation> errors = new ArrayList<>();

        @Override
        public void recordError(CacheKey key, CacheOperation operation, Throwable failure) {
            errors.add(operation);
        }
    }

    private static final class TrackingProvider implements CacheProvider {
        private int deletions;
        private RuntimeException failure;

        @Override
        public String name() { return "test"; }

        @Override
        public <T> CacheValue<T> get(CacheKey key, Class<T> valueType, CacheSpec spec) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public void put(CacheKey key, Object value, Duration ttl, CacheSpec spec) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public void putNullValue(CacheKey key, Duration ttl, CacheSpec spec) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public void evict(CacheKey key) {
            if (failure != null) { throw failure; }
            deletions++;
        }
    }
}
