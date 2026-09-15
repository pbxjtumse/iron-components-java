package com.xjtu.iron.distributed.lock.starter.observability;

import com.xjtu.iron.distributed.lock.core.fencing.registry.FencingTokenProviderRegistry;
import com.xjtu.iron.distributed.lock.provider.jdbc.fencing.JdbcFencingTokenConstants;
import com.xjtu.iron.distributed.lock.provider.redisson.RedissonLockConstants;
import com.xjtu.iron.distributed.lock.spi.LockProvider;
import com.xjtu.iron.distributed.lock.spi.LockProviderCapabilities;
import com.xjtu.iron.distributed.lock.spi.LockProviderRegistry;
import com.xjtu.iron.distributed.lock.starter.properties.DistributedLockProperties;
import com.xjtu.iron.distributed.lock.starter.properties.JdbcFencingTokenProperties;
import com.xjtu.iron.distributed.lock.starter.properties.RedisDistributedLockProperties;
import com.xjtu.iron.distributed.lock.starter.properties.RedissonDistributedLockProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * 分布式锁组件健康检查。
 *
 * <p>检查装配状态、能力矩阵和 fencing 配置一致性；不会主动创建锁或递增 token。</p>
 */
public final class DistributedLockHealthIndicator implements HealthIndicator {

    private final LockProviderRegistry providerRegistry;
    private final FencingTokenProviderRegistry fencingRegistry;
    private final DistributedLockProperties properties;
    private final RedisDistributedLockProperties redisProperties;
    private final JdbcFencingTokenProperties jdbcFencingProperties;
    private final RedissonDistributedLockProperties redissonProperties;

    public DistributedLockHealthIndicator(LockProviderRegistry providerRegistry, FencingTokenProviderRegistry fencingRegistry,
            DistributedLockProperties properties, RedisDistributedLockProperties redisProperties, JdbcFencingTokenProperties jdbcFencingProperties,
            RedissonDistributedLockProperties redissonProperties) {
        this.providerRegistry = providerRegistry;
        this.fencingRegistry = fencingRegistry;
        this.properties = properties;
        this.redisProperties = redisProperties;
        this.jdbcFencingProperties = jdbcFencingProperties;
        this.redissonProperties = redissonProperties;
    }

    @Override
    public Health health() {
        if (!properties.isEnabled()) {
            return Health.up()
                    .withDetail("enabled", false)
                    .withDetail("message", "distributed lock auto configuration is disabled")
                    .build();
        }

        String configuredLockProvider = properties.getDefaultProvider();
        try {
            LockProvider lockProvider = providerRegistry.getDefaultProvider();
            LockProviderCapabilities capabilities = lockProvider.capabilities();
            String configuredFencingProvider = trimToNull(properties.getFencingTokenProviderName());
            boolean jdbcEnabled = jdbcFencingProperties != null && jdbcFencingProperties.isEnabled();
            boolean jdbcProviderRegistered = fencingRegistry.findProvider(JdbcFencingTokenConstants.PROVIDER_NAME).isPresent();
            boolean fencingReady = isFencingReady(lockProvider, capabilities, configuredFencingProvider);
            boolean jdbcConfigurationReady = !jdbcEnabled || jdbcProviderRegistered;

            // redisson.enabled 默认 false，因此一旦用户显式打开，就要求 Provider 真正完成装配。
            // 这样可以发现：开启了 Redisson，但 RedissonClient 缺失 / Bean 选择失败 / 自动创建条件不满足。
            boolean redissonEnabled = redissonProperties != null && redissonProperties.isEnabled();
            boolean redissonProviderRegistered = providerRegistry.containsProvider(RedissonLockConstants.PROVIDER_NAME);
            boolean redissonConfigurationReady = !redissonEnabled || redissonProviderRegistered;

            Health.Builder builder = fencingReady && jdbcConfigurationReady && redissonConfigurationReady
                    ? Health.up()
                    : Health.down();

            return builder
                    .withDetail("enabled", true)
                    .withDetail("defaultProvider", configuredLockProvider)
                    .withDetail("actualProvider", lockProvider.providerName())
                    .withDetail("redisEnabled", redisProperties == null || redisProperties.isEnabled())
                    .withDetail("keyPrefix", redisProperties == null ? null : redisProperties.getKeyPrefix())
                    .withDetail("fencingRequiredByDefault", properties.isFencingRequired())
                    .withDetail("configuredFencingProvider", configuredFencingProvider)
                    .withDetail("nativeFencingSupported", capabilities.isFencingTokenSupported())
                    .withDetail("externalFencingProviders", fencingRegistry.providerNames())
                    .withDetail("fencingReady", fencingReady)
                    .withDetail("jdbcFencingEnabled", jdbcEnabled)
                    .withDetail("jdbcFencingProviderRegistered", jdbcProviderRegistered)
                    .withDetail("jdbcFencingTable", jdbcFencingProperties == null ? null : jdbcFencingProperties.getTableName())
                    .withDetail("autoRenewSupported", capabilities.isAutoRenewSupported())
                    .withDetail("autoRenewMode", capabilities.getAutoRenewMode().name())
                    .withDetail("manualRenewSupported", capabilities.isManualRenewSupported())
                    .withDetail("redissonEnabled", redissonEnabled)
                    .withDetail("redissonProviderRegistered", redissonProviderRegistered)
                    .withDetail("redissonConfigurationReady", redissonConfigurationReady)
                    .withDetail("redissonWatchdogTimeout", redissonProperties == null ? null : redissonProperties.getWatchdogTimeout())
                    .withDetail("nativeWaitSupported", capabilities.isNativeWaitSupported())
                    .withDetail("fairLockSupported", capabilities.isFairLockSupported())
                    .withDetail("reentrantSupported", capabilities.isReentrantSupported())
                    .build();
        } catch (Throwable ex) {
            return Health.down(ex)
                    .withDetail("enabled", true)
                    .withDetail("defaultProvider", configuredLockProvider)
                    .build();
        }
    }

    private boolean isFencingReady(LockProvider lockProvider, LockProviderCapabilities capabilities, String configuredFencingProvider) {
        if (!properties.isFencingRequired()) {
            return true;
        }
        if (configuredFencingProvider != null) {
            if (configuredFencingProvider.equals(lockProvider.providerName())) {
                return capabilities.isFencingTokenSupported();
            }
            return fencingRegistry.findProvider(configuredFencingProvider).isPresent();
        }
        // 未显式指定 external provider 时，只允许使用当前 LockProvider 的 native fencing；
        // 不再从 registry 猜一个默认外部发号器，和 FencingTokenCoordinator 保持同一规则。
        return capabilities.isFencingTokenSupported();
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
