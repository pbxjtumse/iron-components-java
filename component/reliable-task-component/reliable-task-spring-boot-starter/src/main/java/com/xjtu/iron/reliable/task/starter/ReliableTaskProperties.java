package com.xjtu.iron.reliable.task.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Reliable Task v1 配置。 */
@ConfigurationProperties(prefix = "xjtu.iron.reliable-task")
public class ReliableTaskProperties {

    private boolean enabled = true;
    private String tableName = "iron_reliable_task";
    private String ownerId;
    private int defaultMaxAttempts = 10;
    private int scanBucketCount = 64;
    private Duration leaseDuration = Duration.ofMinutes(1);
    private Duration failureRetryDelay = Duration.ofMinutes(1);
    private final LocalScheduler localScheduler = new LocalScheduler();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
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
        private boolean enabled;
        private Duration fixedDelay = Duration.ofSeconds(5);
        private String storeName = "default";
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
