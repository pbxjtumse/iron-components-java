package com.xjtu.iron.concurrency.starter.properties;

import com.xjtu.iron.concurrency.api.enums.QueueType;
import com.xjtu.iron.concurrency.api.enums.RejectionPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * XJTU Iron 并发组件配置属性。
 */
@ConfigurationProperties(prefix = "xjtu.iron.concurrency")
public class XjtuIronConcurrencyProperties {

    /**
     * 是否启用并发组件。
     */
    private boolean enabled = true;

    /**
     * 默认线程池名称。
     */
    private String defaultExecutor = "default";

    /**
     * 上下文传播配置。
     */
    private ContextProperties context = new ContextProperties();

    /**
     * 诊断配置。
     */
    private DiagnosticsProperties diagnostics = new DiagnosticsProperties();

    /**
     * 管理接口配置。
     */
    private ManagementProperties management = new ManagementProperties();

    /**
     * 任务状态注册表配置。
     */
    private TaskRegistryProperties taskRegistry = new TaskRegistryProperties();

    /**
     * timeout 与 fallback 结果管道配置。
     */
    private PipelineProperties pipeline = new PipelineProperties();

    /**
     * 线程池配置集合。
     */
    private Map<String, ThreadPoolProperties> threadPools = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultExecutor() {
        return defaultExecutor;
    }

    public void setDefaultExecutor(String defaultExecutor) {
        this.defaultExecutor = defaultExecutor;
    }

    public ContextProperties getContext() {
        return context;
    }

    public void setContext(ContextProperties context) {
        this.context = context;
    }

    public DiagnosticsProperties getDiagnostics() {
        return diagnostics;
    }

    public void setDiagnostics(DiagnosticsProperties diagnostics) {
        this.diagnostics = diagnostics;
    }

    public ManagementProperties getManagement() {
        return management;
    }

    public void setManagement(ManagementProperties management) {
        this.management = management;
    }

    public TaskRegistryProperties getTaskRegistry() {
        return taskRegistry;
    }

    public void setTaskRegistry(TaskRegistryProperties taskRegistry) {
        this.taskRegistry = taskRegistry;
    }

    public PipelineProperties getPipeline() {
        return pipeline;
    }

    public void setPipeline(PipelineProperties pipeline) {
        this.pipeline = pipeline == null ? new PipelineProperties() : pipeline;
    }

    public Map<String, ThreadPoolProperties> getThreadPools() {
        return threadPools;
    }

    public void setThreadPools(Map<String, ThreadPoolProperties> threadPools) {
        this.threadPools = threadPools;
    }

    /**
     * 上下文传播配置。
     */
    public static class ContextProperties {
        private boolean enabled = true;
        private boolean mdcEnabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isMdcEnabled() {
            return mdcEnabled;
        }

        public void setMdcEnabled(boolean mdcEnabled) {
            this.mdcEnabled = mdcEnabled;
        }
    }

    /**
     * 线程池诊断配置。
     */
    public static class DiagnosticsProperties {
        private boolean enabled = true;
        private double activeUsageWarnThreshold = 0.9D;
        private double queueUsageWarnThreshold = 0.8D;
        private boolean downWhenBusy = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getActiveUsageWarnThreshold() {
            return activeUsageWarnThreshold;
        }

        public void setActiveUsageWarnThreshold(double activeUsageWarnThreshold) {
            this.activeUsageWarnThreshold = activeUsageWarnThreshold;
        }

        public double getQueueUsageWarnThreshold() {
            return queueUsageWarnThreshold;
        }

        public void setQueueUsageWarnThreshold(double queueUsageWarnThreshold) {
            this.queueUsageWarnThreshold = queueUsageWarnThreshold;
        }

        public boolean isDownWhenBusy() {
            return downWhenBusy;
        }

        public void setDownWhenBusy(boolean downWhenBusy) {
            this.downWhenBusy = downWhenBusy;
        }
    }

    /**
     * 只读管理接口配置。
     */
    public static class ManagementProperties {
        private boolean enabled = true;
        private String basePath = "/iron-concurrency";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBasePath() {
            return basePath;
        }

        public void setBasePath(String basePath) {
            this.basePath = basePath;
        }
    }

    /**
     * 任务状态注册表配置。
     */
    public static class TaskRegistryProperties {
        private int maxSize = 10000;

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }
    }

    /**
     * timeout 与 fallback 结果处理管道配置。
     */
    public static class PipelineProperties {

        /**
         * 结果层超时调度线程数。
         *
         * <p>调度线程只执行短小的超时判定，不应执行 fallback 或业务 RPC。</p>
         */
        private int timeoutSchedulerSize = 1;

        /**
         * 超时调度线程名称前缀。
         */
        private String timeoutThreadNamePrefix = "iron-concurrency-timeout-";

        /**
         * 超时调度线程是否为 daemon。
         */
        private boolean timeoutDaemon = true;

        /**
         * fallback 执行器核心线程数。
         */
        private int fallbackCorePoolSize = 2;

        /**
         * fallback 执行器最大线程数。
         */
        private int fallbackMaxPoolSize = 8;

        /**
         * fallback 执行器有界队列容量。
         */
        private int fallbackQueueCapacity = 1024;

        /**
         * fallback 非核心线程空闲回收时间。
         */
        private Duration fallbackKeepAliveTime = Duration.ofSeconds(60);

        /**
         * 应用关闭时等待 fallback 执行器处理已入队 fallback 任务的最长时间。
         */
        private Duration fallbackAwaitTermination = Duration.ofSeconds(5);

        /**
         * fallback 线程名称前缀。
         */
        private String fallbackThreadNamePrefix = "iron-concurrency-fallback-";

        /**
         * fallback 线程是否为 daemon。
         */
        private boolean fallbackDaemon = true;

        /**
         * fallback 执行器拒绝策略。
         *
         * <p>
         * 只允许 ABORT 或 CALLER_RUNS。DISCARD 会导致 fallback 结果无法完成，
         * BLOCKING_WAIT 会阻塞完成原始 Future 的线程，因此不适用于内部恢复管道。
         * </p>
         */
        private RejectionPolicy fallbackRejectionPolicy = RejectionPolicy.ABORT;

        public int getTimeoutSchedulerSize() {
            return timeoutSchedulerSize;
        }

        public void setTimeoutSchedulerSize(int timeoutSchedulerSize) {
            this.timeoutSchedulerSize = timeoutSchedulerSize;
        }

        public String getTimeoutThreadNamePrefix() {
            return timeoutThreadNamePrefix;
        }

        public void setTimeoutThreadNamePrefix(String timeoutThreadNamePrefix) {
            this.timeoutThreadNamePrefix = timeoutThreadNamePrefix;
        }

        public boolean isTimeoutDaemon() {
            return timeoutDaemon;
        }

        public void setTimeoutDaemon(boolean timeoutDaemon) {
            this.timeoutDaemon = timeoutDaemon;
        }

        public int getFallbackCorePoolSize() {
            return fallbackCorePoolSize;
        }

        public void setFallbackCorePoolSize(int fallbackCorePoolSize) {
            this.fallbackCorePoolSize = fallbackCorePoolSize;
        }

        public int getFallbackMaxPoolSize() {
            return fallbackMaxPoolSize;
        }

        public void setFallbackMaxPoolSize(int fallbackMaxPoolSize) {
            this.fallbackMaxPoolSize = fallbackMaxPoolSize;
        }

        public int getFallbackQueueCapacity() {
            return fallbackQueueCapacity;
        }

        public void setFallbackQueueCapacity(int fallbackQueueCapacity) {
            this.fallbackQueueCapacity = fallbackQueueCapacity;
        }

        public Duration getFallbackKeepAliveTime() {
            return fallbackKeepAliveTime;
        }

        public void setFallbackKeepAliveTime(Duration fallbackKeepAliveTime) {
            this.fallbackKeepAliveTime = fallbackKeepAliveTime;
        }

        public Duration getFallbackAwaitTermination() {
            return fallbackAwaitTermination;
        }

        public void setFallbackAwaitTermination(Duration fallbackAwaitTermination) {
            this.fallbackAwaitTermination = fallbackAwaitTermination;
        }

        public String getFallbackThreadNamePrefix() {
            return fallbackThreadNamePrefix;
        }

        public void setFallbackThreadNamePrefix(String fallbackThreadNamePrefix) {
            this.fallbackThreadNamePrefix = fallbackThreadNamePrefix;
        }

        public boolean isFallbackDaemon() {
            return fallbackDaemon;
        }

        public void setFallbackDaemon(boolean fallbackDaemon) {
            this.fallbackDaemon = fallbackDaemon;
        }

        public RejectionPolicy getFallbackRejectionPolicy() {
            return fallbackRejectionPolicy;
        }

        public void setFallbackRejectionPolicy(RejectionPolicy fallbackRejectionPolicy) {
            this.fallbackRejectionPolicy = fallbackRejectionPolicy;
        }

        /**
         * 校验管道配置。
         */
        public void validate() {
            if (timeoutSchedulerSize <= 0) {
                throw new IllegalArgumentException("pipeline.timeoutSchedulerSize must be greater than 0");
            }
            if (fallbackCorePoolSize <= 0) {
                throw new IllegalArgumentException("pipeline.fallbackCorePoolSize must be greater than 0");
            }
            if (fallbackMaxPoolSize < fallbackCorePoolSize) {
                throw new IllegalArgumentException(
                        "pipeline.fallbackMaxPoolSize must be greater than or equal to fallbackCorePoolSize"
                );
            }
            if (fallbackQueueCapacity <= 0) {
                throw new IllegalArgumentException("pipeline.fallbackQueueCapacity must be greater than 0");
            }
            if (fallbackKeepAliveTime == null
                    || fallbackKeepAliveTime.isZero()
                    || fallbackKeepAliveTime.isNegative()) {
                throw new IllegalArgumentException(
                        "pipeline.fallbackKeepAliveTime must be greater than 0"
                );
            }
            if (fallbackAwaitTermination == null
                    || fallbackAwaitTermination.isZero()
                    || fallbackAwaitTermination.isNegative()) {
                throw new IllegalArgumentException(
                        "pipeline.fallbackAwaitTermination must be greater than 0"
                );
            }
            if (fallbackRejectionPolicy != RejectionPolicy.ABORT
                    && fallbackRejectionPolicy != RejectionPolicy.CALLER_RUNS) {
                throw new IllegalArgumentException(
                        "pipeline.fallbackRejectionPolicy only supports ABORT or CALLER_RUNS"
                );
            }
        }
    }

    /**
     * 单个线程池配置。
     */
    public static class ThreadPoolProperties {
        private int corePoolSize = 4;
        private int maxPoolSize = 16;
        private int queueCapacity = 1000;
        private Duration keepAliveTime = Duration.ofSeconds(60);
        private boolean allowCoreThreadTimeout = false;
        private String threadNamePrefix;
        private QueueType queueType = QueueType.BOUNDED_LINKED_BLOCKING_QUEUE;
        private RejectionPolicy rejectionPolicy = RejectionPolicy.ABORT;
        private Duration rejectionWaitTime = Duration.ofMillis(100);
        private boolean waitForTasksToCompleteOnShutdown = true;
        private Duration awaitTermination = Duration.ofSeconds(30);

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

        public Duration getRejectionWaitTime() {
            return rejectionWaitTime;
        }

        public void setRejectionWaitTime(Duration rejectionWaitTime) {
            this.rejectionWaitTime = rejectionWaitTime;
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
    }
}
