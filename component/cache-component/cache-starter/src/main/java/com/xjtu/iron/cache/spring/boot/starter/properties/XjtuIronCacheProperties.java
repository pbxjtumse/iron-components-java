package com.xjtu.iron.cache.spring.boot.starter.properties;

import com.xjtu.iron.cache.api.enums.CacheDegradePolicy;
import com.xjtu.iron.cache.api.enums.CacheNullPolicy;
import com.xjtu.iron.cache.core.event.CacheEventPublishFailurePolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * XJTU Iron Cache 组件配置属性。
 *
 * <p>对应配置前缀：</p>
 *
 * <pre>
 * xjtu.iron.cache
 * </pre>
 *
 * <p>这个类只承载配置，不包含业务逻辑。当前配置量不大，内部配置类先保留为
 * static inner class；后续如果字段明显增多，再拆成独立 properties 类。</p>
 */
@ConfigurationProperties(prefix = "xjtu.iron.cache")
public class XjtuIronCacheProperties {

    /**
     * 是否启用缓存组件自动装配。
     */
    private boolean enabled = true;

    /**
     * Caffeine L1 本地缓存配置。
     */
    private CaffeineProperties caffeine = new CaffeineProperties();

    /**
     * Redis L2 分布式缓存配置。
     */
    private RedisProperties redis = new RedisProperties();


    /**
     * cacheName 到缓存策略配置的映射。
     *
     * <p>例如：</p>
     *
     * <pre>
     * specs:
     *   campaignRule:
     *     ttl: 5m
     *     enable-l1: true
     * </pre>
     */
    private Map<String, CacheSpecProperties> specs = new HashMap<>();

    private ApplicationProperties application = new ApplicationProperties();

    private EventProperties event = new EventProperties();

    /**
     * 获取是否启用缓存组件。
     *
     * @return true 表示启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用缓存组件。
     *
     * @param enabled true 表示启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取 Caffeine 本地缓存配置。
     *
     * @return Caffeine 配置
     */
    public CaffeineProperties getCaffeine() {
        return caffeine;
    }

    /**
     * 设置 Caffeine 本地缓存配置。
     *
     * @param caffeine Caffeine 配置
     */
    public void setCaffeine(CaffeineProperties caffeine) {
        this.caffeine = caffeine;
    }

    /**
     * 获取 Redis 分布式缓存配置。
     *
     * @return Redis 配置
     */
    public RedisProperties getRedis() {
        return redis;
    }

    /**
     * 设置 Redis 分布式缓存配置。
     *
     * @param redis Redis 配置
     */
    public void setRedis(RedisProperties redis) {
        this.redis = redis;
    }

    /**
     * 获取 cacheName 到缓存策略配置的映射。
     *
     * @return 缓存策略配置映射
     */
    public Map<String, CacheSpecProperties> getSpecs() {
        return specs;
    }

    /**
     * 设置 cacheName 到缓存策略配置的映射。
     *
     * @param specs 缓存策略配置映射
     */
    public void setSpecs(Map<String, CacheSpecProperties> specs) {
        this.specs = specs;
    }

    public ApplicationProperties getApplication() {
        return application;
    }

    public void setApplication(ApplicationProperties application) {
        this.application = application;
    }

    public EventProperties getEvent() {
        return event;
    }

    public void setEvent(EventProperties event) {
        this.event = event;
    }



    /**
     * Caffeine L1 本地缓存配置项。
     */
    public static class CaffeineProperties {

        /**
         * 是否启用 Caffeine L1 本地缓存。
         */
        private boolean enabled = true;

        /**
         * 每个 cacheName 对应的本地缓存最大条目数。
         */
        private long defaultMaximumSize = 10000;

        /**
         * 获取是否启用 Caffeine。
         *
         * @return true 表示启用
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用 Caffeine。
         *
         * @param enabled true 表示启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取默认最大本地缓存条目数。
         *
         * @return 最大条目数
         */
        public long getDefaultMaximumSize() {
            return defaultMaximumSize;
        }

        /**
         * 设置默认最大本地缓存条目数。
         *
         * @param defaultMaximumSize 最大条目数
         */
        public void setDefaultMaximumSize(long defaultMaximumSize) {
            this.defaultMaximumSize = defaultMaximumSize;
        }
    }

    /**
     * Redis L2 分布式缓存配置项。
     */
    public static class RedisProperties {

        /**
         * 是否启用 Redis L2 分布式缓存。
         */
        private boolean enabled = true;

        /**
         * Redis key 全局前缀。
         *
         * <p>建议 demo 使用 cache-demo:，生产按应用或环境隔离。</p>
         */
        private String keyPrefix = "";

        /**
         * 获取是否启用 Redis。
         *
         * @return true 表示启用
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用 Redis。
         *
         * @param enabled true 表示启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 获取 Redis key 前缀。
         *
         * @return Redis key 前缀
         */
        public String getKeyPrefix() {
            return keyPrefix;
        }

        /**
         * 设置 Redis key 前缀。
         *
         * @param keyPrefix Redis key 前缀
         */
        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }
    }

    /**
     * 单个 cacheName 的缓存策略配置项。
     */
    public static class CacheSpecProperties {

        /**
         * 是否启用 L1 Caffeine 本地缓存。
         */
        private boolean enableL1 = true;

        /**
         * 是否启用 L2 Redis 分布式缓存。
         */
        private boolean enableL2 = true;

        /**
         * 正常值 TTL。
         */
        private Duration ttl = Duration.ofMinutes(10);

        /**
         * 空值缓存 TTL。
         */
        private Duration nullValueTtl = Duration.ofSeconds(30);

        /**
         * TTL 随机抖动范围。
         */
        private Duration ttlJitter = Duration.ofSeconds(60);

        /**
         * 是否启用本地互斥加载。
         */
        private boolean mutexLoad = true;

        /**
         * 空值缓存策略。
         */
        private CacheNullPolicy nullPolicy = CacheNullPolicy.CACHE_NULL;

        /**
         * 缓存读取异常后的降级策略。
         */
        private CacheDegradePolicy degradePolicy = CacheDegradePolicy.LOAD_SOURCE;

        public boolean isEnableL1() {
            return enableL1;
        }

        public void setEnableL1(boolean enableL1) {
            this.enableL1 = enableL1;
        }

        public boolean isEnableL2() {
            return enableL2;
        }

        public void setEnableL2(boolean enableL2) {
            this.enableL2 = enableL2;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public Duration getNullValueTtl() {
            return nullValueTtl;
        }

        public void setNullValueTtl(Duration nullValueTtl) {
            this.nullValueTtl = nullValueTtl;
        }

        public Duration getTtlJitter() {
            return ttlJitter;
        }

        public void setTtlJitter(Duration ttlJitter) {
            this.ttlJitter = ttlJitter;
        }

        public boolean isMutexLoad() {
            return mutexLoad;
        }

        public void setMutexLoad(boolean mutexLoad) {
            this.mutexLoad = mutexLoad;
        }

        public CacheNullPolicy getNullPolicy() {
            return nullPolicy;
        }

        public void setNullPolicy(CacheNullPolicy nullPolicy) {
            this.nullPolicy = nullPolicy;
        }

        public CacheDegradePolicy getDegradePolicy() {
            return degradePolicy;
        }

        public void setDegradePolicy(CacheDegradePolicy degradePolicy) {
            this.degradePolicy = degradePolicy;
        }
    }

    public static class ApplicationProperties {

        /**
         * 应用名。
         *
         * <p>默认可以从 spring.application.name 注入，starter 里也可以兜底。</p>
         */
        private String name = "unknown-application";

        /**
         * 实例 ID。
         *
         * <p>用于 Redis Pub/Sub 事件去重。</p>
         *
         * <p>如果不配置，starter 可以自动生成 UUID。</p>
         */
        private String instanceId;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getInstanceId() {
            return instanceId;
        }

        public void setInstanceId(String instanceId) {
            this.instanceId = instanceId;
        }
    }

    public static class EventProperties {

        /**
         * 是否启用缓存事件。
         *
         * <p>二期第一版主要控制 Redis Pub/Sub 本地缓存失效通知。</p>
         */
        private boolean enabled = false;

        /**
         * Redis Pub/Sub channel。
         */
        private String channel = "xjtu:iron:cache:event";

        private CacheEventPublishFailurePolicy publishFailurePolicy = CacheEventPublishFailurePolicy.IGNORE;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getChannel() {
            return channel;
        }

        public void setChannel(String channel) {
            this.channel = channel;
        }

        public CacheEventPublishFailurePolicy getPublishFailurePolicy() {
            return publishFailurePolicy;
        }

        public void setPublishFailurePolicy(CacheEventPublishFailurePolicy publishFailurePolicy) {
            this.publishFailurePolicy = publishFailurePolicy;
        }
    }
}
