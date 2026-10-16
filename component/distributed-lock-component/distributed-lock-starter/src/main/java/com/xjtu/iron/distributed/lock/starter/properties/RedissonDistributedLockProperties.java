package com.xjtu.iron.distributed.lock.starter.properties;

import com.xjtu.iron.distributed.lock.provider.redisson.RedissonLockConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Redisson LockProvider 自身语义配置。
 *
 * <p>Redis host/port/database/password 不在这里重复定义，仍然来自全局 spring.data.redis.*。
 * 如果应用已经自己创建 RedissonClient，可以通过 clientBeanName 选择已有 Bean。</p>
 */
@ConfigurationProperties(prefix = "xjtu.iron.distributed-lock.redisson")
public class RedissonDistributedLockProperties {

    /** 是否启用 Redisson LockProvider。默认关闭，显式打开后才创建第二套 Redis 客户端。 */
    private boolean enabled = false;

    /** Redisson 锁对象名称前缀。 */
    private String keyPrefix = RedissonLockConstants.DEFAULT_KEY_PREFIX;

    /** 多 RedissonClient Bean 时显式指定要使用的 Bean 名称。 */
    private String clientBeanName;

    /** Redisson 原生 watchdog timeout。autoRenew=true 时 LockOptions.leaseTime 必须与其一致。 */
    private Duration watchdogTimeout = Duration.ofSeconds(30);

    /** 获取锁后是否等待已连接副本确认同步。Redisson 默认 true。 */
    private boolean checkLockSyncedSlaves = true;

    /** 副本同步确认超时。 */
    private Duration slavesSyncTimeout = Duration.ofSeconds(1);

    /** 没有 RedissonClient Bean 时，是否允许根据 spring.data.redis.* 自动创建 RedissonClient（standalone/sentinel/cluster）。 */
    private boolean createClientIfMissing = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
    public String getClientBeanName() { return clientBeanName; }
    public void setClientBeanName(String clientBeanName) { this.clientBeanName = clientBeanName; }
    public Duration getWatchdogTimeout() { return watchdogTimeout; }
    public void setWatchdogTimeout(Duration watchdogTimeout) { this.watchdogTimeout = watchdogTimeout; }
    public boolean isCheckLockSyncedSlaves() { return checkLockSyncedSlaves; }
    public void setCheckLockSyncedSlaves(boolean checkLockSyncedSlaves) { this.checkLockSyncedSlaves = checkLockSyncedSlaves; }
    public Duration getSlavesSyncTimeout() { return slavesSyncTimeout; }
    public void setSlavesSyncTimeout(Duration slavesSyncTimeout) { this.slavesSyncTimeout = slavesSyncTimeout; }
    public boolean isCreateClientIfMissing() { return createClientIfMissing; }
    public void setCreateClientIfMissing(boolean createClientIfMissing) { this.createClientIfMissing = createClientIfMissing; }
}
