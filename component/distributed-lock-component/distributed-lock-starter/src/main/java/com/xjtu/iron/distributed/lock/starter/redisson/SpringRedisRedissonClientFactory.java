package com.xjtu.iron.distributed.lock.starter.redisson;

import com.xjtu.iron.distributed.lock.starter.properties.RedissonDistributedLockProperties;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.redisson.config.SentinelServersConfig;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisConnectionDetails;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.ssl.SslBundle;

import java.time.Duration;

/**
 * 根据 Spring Boot {@code spring.data.redis.*} 已解析出的连接详情创建 RedissonClient。
 *
 * <p>目标是“连接参数只配置一次”：缓存、自研 Redis Lua Provider 继续使用 Spring Data Redis，
 * Redisson Provider 创建自己的 RedissonClient 连接池，但复用同一套 host/port/database/credentials/SSL 信息。</p>
 *
 * <p>支持 Spring Boot 3.5 的 standalone / sentinel / cluster 三种 {@link RedisConnectionDetails} 拓扑。
 * Redisson 与 Lettuce 是两个不同客户端，因此只共享配置，不共享物理连接池。</p>
 */
public final class SpringRedisRedissonClientFactory {

    private SpringRedisRedissonClientFactory() {}

    public static RedissonClient create(RedisConnectionDetails details, RedisProperties redisProperties,
            RedissonDistributedLockProperties lockProperties) {
        if (details == null) {
            throw new IllegalStateException("RedisConnectionDetails is required to auto-create RedissonClient");
        }

        Config config = new Config();
        configureLockReliability(config, lockProperties);
        configureCredentials(config, details);

        if (details.getSentinel() != null) {
            configureSentinel(config, details, redisProperties);
        } else if (details.getCluster() != null) {
            configureCluster(config, details, redisProperties);
        } else if (details.getStandalone() != null) {
            configureStandalone(config, details, redisProperties);
        } else {
            throw new IllegalStateException("unsupported RedisConnectionDetails topology");
        }

        return Redisson.create(config);
    }

    private static void configureLockReliability(Config config, RedissonDistributedLockProperties properties) {
        config.setLockWatchdogTimeout(requirePositiveMillis(properties.getWatchdogTimeout(), "watchdogTimeout"));
        config.setCheckLockSyncedSlaves(properties.isCheckLockSyncedSlaves());
        config.setSlavesSyncTimeout(requirePositiveMillis(properties.getSlavesSyncTimeout(), "slavesSyncTimeout"));
    }

    private static void configureCredentials(Config config, RedisConnectionDetails details) {
        if (hasText(details.getUsername())) {
            config.setUsername(details.getUsername());
        }
        if (hasText(details.getPassword())) {
            config.setPassword(details.getPassword());
        }
    }

    private static void configureStandalone(Config config, RedisConnectionDetails details, RedisProperties properties) {
        RedisConnectionDetails.Standalone standalone = details.getStandalone();
        String scheme = scheme(standalone.getSslBundle(), properties);
        applySslBundle(config, standalone.getSslBundle());

        SingleServerConfig server = config.useSingleServer()
                .setAddress(scheme + standalone.getHost() + ':' + standalone.getPort())
                .setDatabase(standalone.getDatabase());
        applyTimeouts(server, properties);
    }

    private static void configureSentinel(Config config, RedisConnectionDetails details, RedisProperties properties) {
        RedisConnectionDetails.Sentinel sentinel = details.getSentinel();
        String scheme = scheme(sentinel.getSslBundle(), properties);
        applySslBundle(config, sentinel.getSslBundle());

        SentinelServersConfig servers = config.useSentinelServers()
                .setMasterName(sentinel.getMaster())
                .setDatabase(sentinel.getDatabase());
        sentinel.getNodes().forEach(node ->
                servers.addSentinelAddress(scheme + node.host() + ':' + node.port()));
        if (hasText(sentinel.getUsername())) {
            servers.setSentinelUsername(sentinel.getUsername());
        }
        if (hasText(sentinel.getPassword())) {
            servers.setSentinelPassword(sentinel.getPassword());
        }
        applyTimeouts(servers, properties);
    }

    private static void configureCluster(Config config, RedisConnectionDetails details, RedisProperties properties) {
        RedisConnectionDetails.Cluster cluster = details.getCluster();
        String scheme = scheme(cluster.getSslBundle(), properties);
        applySslBundle(config, cluster.getSslBundle());

        ClusterServersConfig servers = config.useClusterServers();
        cluster.getNodes().forEach(node ->
                servers.addNodeAddress(scheme + node.host() + ':' + node.port()));
        applyTimeouts(servers, properties);
    }

    private static void applyTimeouts(org.redisson.config.BaseConfig<?> config, RedisProperties properties) {
        if (properties == null) {
            return;
        }
        if (properties.getTimeout() != null) {
            config.setTimeout(toIntMillis(properties.getTimeout(), "spring.data.redis.timeout"));
        }
        if (properties.getConnectTimeout() != null) {
            config.setConnectTimeout(toIntMillis(properties.getConnectTimeout(), "spring.data.redis.connect-timeout"));
        }
        if (hasText(properties.getClientName())) {
            config.setClientName(properties.getClientName());
        }
    }

    /** 把 Spring Boot SSL bundle 中的 trust/key manager 直接交给 Redisson Config。 */
    private static void applySslBundle(Config config, SslBundle bundle) {
        if (bundle == null) {
            return;
        }
        config.setSslTrustManagerFactory(bundle.getManagers().getTrustManagerFactory());
        config.setSslKeyManagerFactory(bundle.getManagers().getKeyManagerFactory());
        if (bundle.getOptions().getEnabledProtocols() != null) {
            config.setSslProtocols(bundle.getOptions().getEnabledProtocols());
        }
        if (bundle.getOptions().getCiphers() != null) {
            config.setSslCiphers(bundle.getOptions().getCiphers());
        }
    }

    private static String scheme(SslBundle bundle, RedisProperties properties) {
        boolean ssl = bundle != null || (properties != null && properties.getSsl().isEnabled());
        return ssl ? "rediss://" : "redis://";
    }

    private static int toIntMillis(Duration value, String name) {
        if (value == null || value.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        long millis = value.toMillis();
        if (millis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(name + " is too large: " + value);
        }
        return (int) millis;
    }

    private static long requirePositiveMillis(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value.toMillis();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
