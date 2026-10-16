package com.xjtu.iron.concurrency.api.execution.pool;

import com.xjtu.iron.concurrency.api.enums.QueueType;
import com.xjtu.iron.concurrency.api.enums.RejectionPolicy;

import java.time.Duration;

/**
 * 线程池配置模型。
 *
 * <p>用于描述一个业务线程池。</p>
 */
public class ThreadPoolSpec {

    /**
     * 线程池名称。
     */
    private String name;

    /**
     * 核心线程数。
     */
    private int corePoolSize = 4;

    /**
     * 最大线程数。
     */
    private int maxPoolSize = 16;

    /**
     * 队列容量。
     */
    private int queueCapacity = 1000;

    /**
     * 空闲线程存活时间。
     */
    private Duration keepAliveTime = Duration.ofSeconds(60);

    /**
     * 是否允许核心线程超时。
     */
    private boolean allowCoreThreadTimeout = false;

    /**
     * 线程名前缀。
     */
    private String threadNamePrefix;

    /**
     * BLOCKING_WAIT 拒绝策略下的最大等待时间。
     *
     * <p>只有 rejectionPolicy = BLOCKING_WAIT 时生效。</p>
     */
    private Duration rejectionWaitTime = Duration.ofMillis(100);

    /**
     * 队列类型。
     */
    private QueueType queueType = QueueType.BOUNDED_LINKED_BLOCKING_QUEUE;

    /**
     * 拒绝策略。
     */
    private RejectionPolicy rejectionPolicy = RejectionPolicy.ABORT;

    /**
     * 应用关闭时是否等待任务执行完成。
     */
    private boolean waitForTasksToCompleteOnShutdown = true;

    /**
     * 应用关闭时等待线程池终止的最大时间。
     */
    private Duration awaitTermination = Duration.ofSeconds(30);

    public void validate() {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("thread pool name must not be blank");
        }

        if (corePoolSize <= 0) {
            throw new IllegalArgumentException("corePoolSize must be greater than zero");
        }

        if (maxPoolSize < corePoolSize) {
            throw new IllegalArgumentException("maxPoolSize must be >= corePoolSize");
        }

        if (queueType == null) {
            throw new IllegalArgumentException("queueType must not be null");
        }

        if (queueType == QueueType.DIRECT_HANDOFF) {
            /*
             * SynchronousQueue 不保存元素，容量字段不生效。
             */
            if (queueCapacity < 0) {
                throw new IllegalArgumentException("queueCapacity must be >= 0 for DIRECT_HANDOFF");
            }
        } else if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be greater than zero "
                            + "for bounded queue type "
                            + queueType
            );
        }

        if (keepAliveTime == null || keepAliveTime.isNegative()) {
            throw new IllegalArgumentException("keepAliveTime must not be null or negative");
        }

        if (allowCoreThreadTimeout && (keepAliveTime.isZero() || keepAliveTime.isNegative())) {
            throw new IllegalArgumentException("keepAliveTime must be greater than zero when allowCoreThreadTimeout=true");
        }

        if (rejectionPolicy == null) {
            rejectionPolicy = RejectionPolicy.ABORT;
        }

        if (rejectionPolicy == RejectionPolicy.BLOCKING_WAIT) {
            if (rejectionWaitTime == null || rejectionWaitTime.isZero() || rejectionWaitTime.isNegative()) {
                throw new IllegalArgumentException(
                        "rejectionWaitTime must be greater than zero when rejectionPolicy=BLOCKING_WAIT");
            }
        }

        if (waitForTasksToCompleteOnShutdown) {
            if (awaitTermination == null || awaitTermination.isZero() || awaitTermination.isNegative()) {
                throw new IllegalArgumentException(
                        "awaitTermination must be greater than zero "
                                + "when waitForTasksToCompleteOnShutdown=true"
                );
            }
        }

        if (threadNamePrefix == null || threadNamePrefix.isBlank()) {
            threadNamePrefix = "xjtu-iron-" + name + "-";
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public Duration getKeepAliveTime() {
        return keepAliveTime;
    }

    public void setKeepAliveTime(Duration keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
    }

    public boolean isAllowCoreThreadTimeout() {
        return allowCoreThreadTimeout;
    }

    public void setAllowCoreThreadTimeout(boolean allowCoreThreadTimeout) {
        this.allowCoreThreadTimeout = allowCoreThreadTimeout;
    }

    public String getThreadNamePrefix() {
        return threadNamePrefix;
    }

    public void setThreadNamePrefix(String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }

    public QueueType getQueueType() {
        return queueType;
    }

    public void setQueueType(QueueType queueType) {
        this.queueType = queueType;
    }

    public RejectionPolicy getRejectionPolicy() {
        return rejectionPolicy;
    }

    public void setRejectionPolicy(RejectionPolicy rejectionPolicy) {
        this.rejectionPolicy = rejectionPolicy;
    }

    public boolean isWaitForTasksToCompleteOnShutdown() {
        return waitForTasksToCompleteOnShutdown;
    }

    public void setWaitForTasksToCompleteOnShutdown(boolean waitForTasksToCompleteOnShutdown) {
        this.waitForTasksToCompleteOnShutdown = waitForTasksToCompleteOnShutdown;
    }

    public Duration getAwaitTermination() {
        return awaitTermination;
    }

    public void setAwaitTermination(Duration awaitTermination) {
        this.awaitTermination = awaitTermination;
    }

    public Duration getRejectionWaitTime() {
        return rejectionWaitTime;
    }

    public void setRejectionWaitTime(Duration rejectionWaitTime) {
        this.rejectionWaitTime = rejectionWaitTime;
    }
}
