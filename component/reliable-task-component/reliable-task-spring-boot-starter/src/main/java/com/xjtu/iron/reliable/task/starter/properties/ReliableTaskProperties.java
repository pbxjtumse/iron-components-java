package com.xjtu.iron.reliable.task.starter.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Reliable Task v1 配置。 */
@ConfigurationProperties(prefix = "xjtu.iron.reliable-task")
public class ReliableTaskProperties {

    /** 是否启用 Reliable Task 自动配置。 */
    private boolean enabled = true;
    /** 应用启动时选择的持久化 Provider。 */
    private ReliableTaskProviderType provider = ReliableTaskProviderType.JDBC;
    /** JDBC 和 MyBatis Provider 使用的可靠任务表名。 */
    private String tableName = "iron_reliable_task";
    /** 当前应用实例的租约所有者标识；为空时由 Starter 自动生成。 */
    private String ownerId;
    /** 提交任务未指定时采用的最大尝试次数。 */
    private int defaultMaxAttempts = 10;
    /** 每个存储域划分的扫描桶总数。 */
    private int scanBucketCount = 64;
    /** 每次成功抢占任务后授予执行者的租约时长。 */
    private Duration leaseDuration = Duration.ofMinutes(1);
    /** 处理器抛出未处理异常后的默认重试等待时长。 */
    private Duration failureRetryDelay = Duration.ofMinutes(1);
    /** 内置本地扫描触发器的配置。 */
    private final LocalScheduler localScheduler = new LocalScheduler();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public ReliableTaskProviderType getProvider() { return provider; }
    public void setProvider(ReliableTaskProviderType provider) { this.provider = provider; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public int getDefaultMaxAttempts() { return defaultMaxAttempts; }
    public void setDefaultMaxAttempts(int defaultMaxAttempts) { this.defaultMaxAttempts = defaultMaxAttempts; }
    public int getScanBucketCount() { return scanBucketCount; }
    public void setScanBucketCount(int scanBucketCount) { this.scanBucketCount = scanBucketCount; }
    public Duration getLeaseDuration() { return leaseDuration; }
    public void setLeaseDuration(Duration leaseDuration) { this.leaseDuration = leaseDuration; }
    public Duration getFailureRetryDelay() { return failureRetryDelay; }
    public void setFailureRetryDelay(Duration failureRetryDelay) { this.failureRetryDelay = failureRetryDelay; }
    public LocalScheduler getLocalScheduler() { return localScheduler; }

    public static class LocalScheduler {
        /** 是否启用内置本地扫描触发器。 */
        private boolean enabled;
        /** 两轮全桶扫描之间的固定延迟。 */
        private Duration fixedDelay = Duration.ofSeconds(5);
        /** 本地触发器扫描的逻辑存储域。 */
        private String storeName = "default";
        /** 单个扫描桶每轮最多读取的候选任务数。 */
        private int batchSize = 100;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Duration getFixedDelay() { return fixedDelay; }
        public void setFixedDelay(Duration fixedDelay) { this.fixedDelay = fixedDelay; }
        public String getStoreName() { return storeName; }
        public void setStoreName(String storeName) { this.storeName = storeName; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    }
}
