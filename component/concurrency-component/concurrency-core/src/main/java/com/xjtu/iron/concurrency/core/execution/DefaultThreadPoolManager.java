package com.xjtu.iron.concurrency.core.execution;

import com.xjtu.iron.concurrency.api.execution.pool.ThreadPoolManager;
import com.xjtu.iron.concurrency.api.execution.pool.ThreadPoolSnapshot;
import com.xjtu.iron.concurrency.api.execution.pool.ThreadPoolSpec;
import com.xjtu.iron.concurrency.api.execution.pool.ThreadPoolUpdateRequest;
import com.xjtu.iron.concurrency.core.spi.RejectedExecutionHandlerFactory;
import com.xjtu.iron.concurrency.core.spi.ThreadPoolRegistry;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 默认线程池运行时管理器。
 */
public class DefaultThreadPoolManager implements ThreadPoolManager {

    private final ThreadPoolRegistry threadPoolRegistry;
    private final RejectedExecutionHandlerFactory rejectedExecutionHandlerFactory;

    public DefaultThreadPoolManager(
            ThreadPoolRegistry threadPoolRegistry,
            RejectedExecutionHandlerFactory rejectedExecutionHandlerFactory
    ) {
        this.threadPoolRegistry = threadPoolRegistry;
        this.rejectedExecutionHandlerFactory = rejectedExecutionHandlerFactory;
    }

    @Override
    public ThreadPoolSnapshot snapshot(String executorName) {
        return toSnapshot(executorName, threadPoolRegistry.getExecutor(executorName));
    }

    @Override
    public Map<String, ThreadPoolSnapshot> snapshots() {
        Map<String, ThreadPoolSnapshot> result = new LinkedHashMap<>();
        threadPoolRegistry.snapshot().forEach((name, executor) -> result.put(name, toSnapshot(name, executor)));
        return result;
    }

    @Override
    public ThreadPoolSnapshot resize(String executorName, int corePoolSize, int maximumPoolSize) {
        ThreadPoolUpdateRequest request = new ThreadPoolUpdateRequest();
        request.setCorePoolSize(corePoolSize);
        request.setMaximumPoolSize(maximumPoolSize);
        return update(executorName, request);
    }

    @Override
    public ThreadPoolSnapshot update(String executorName, ThreadPoolUpdateRequest request) {
        ThreadPoolExecutor executor = threadPoolRegistry.getExecutor(executorName);
        if (request == null) {
            return toSnapshot(executorName, executor);
        }

        Integer core = request.getCorePoolSize();
        Integer max = request.getMaximumPoolSize();
        if (core != null && core <= 0) {
            throw new IllegalArgumentException("corePoolSize must be positive");
        }
        if (max != null && max <= 0) {
            throw new IllegalArgumentException("maximumPoolSize must be positive");
        }

        int targetCore = core == null ? executor.getCorePoolSize() : core;
        int targetMax = max == null ? executor.getMaximumPoolSize() : max;
        if (targetMax < targetCore) {
            throw new IllegalArgumentException("maximumPoolSize must be >= corePoolSize");
        }

        // JDK 要求 setCorePoolSize 时不能大于当前 maximumPoolSize，所以这里按安全顺序调整。
        if (targetMax > executor.getMaximumPoolSize()) {
            executor.setMaximumPoolSize(targetMax);
            executor.setCorePoolSize(targetCore);
        } else {
            executor.setCorePoolSize(targetCore);
            executor.setMaximumPoolSize(targetMax);
        }

        Duration keepAliveTime = request.getKeepAliveTime();
        if (keepAliveTime != null && !keepAliveTime.isNegative()) {
            executor.setKeepAliveTime(keepAliveTime.toMillis(), TimeUnit.MILLISECONDS);
        }

        if (request.getAllowCoreThreadTimeout() != null) {
            executor.allowCoreThreadTimeOut(request.getAllowCoreThreadTimeout());
        }

        if (request.getRejectionPolicy() != null) {
            ThreadPoolSpec spec = new ThreadPoolSpec();
            spec.setName(executorName);
            spec.setCorePoolSize(executor.getCorePoolSize());
            spec.setMaxPoolSize(executor.getMaximumPoolSize());
            spec.setRejectionPolicy(request.getRejectionPolicy());
            spec.setRejectionWaitTime(request.getRejectionWaitTime());
            spec.validate();
            executor.setRejectedExecutionHandler(rejectedExecutionHandlerFactory.create(spec));
        }

        if (Boolean.TRUE.equals(request.getPrestartAllCoreThreads())) {
            executor.prestartAllCoreThreads();
        }

        return toSnapshot(executorName, executor);
    }

    private ThreadPoolSnapshot toSnapshot(String executorName, ThreadPoolExecutor executor) {
        ThreadPoolSnapshot snapshot = new ThreadPoolSnapshot();
        int queueSize = executor.getQueue().size();
        int remaining = executor.getQueue().remainingCapacity();
        int queueCapacity = queueSize + remaining;

        snapshot.setExecutorName(executorName);
        snapshot.setCorePoolSize(executor.getCorePoolSize());
        snapshot.setMaximumPoolSize(executor.getMaximumPoolSize());
        snapshot.setPoolSize(executor.getPoolSize());
        snapshot.setLargestPoolSize(executor.getLargestPoolSize());
        snapshot.setActiveCount(executor.getActiveCount());
        snapshot.setQueueSize(queueSize);
        snapshot.setQueueRemainingCapacity(remaining);
        snapshot.setQueueCapacity(queueCapacity);
        snapshot.setActiveUsageRatio(executor.getMaximumPoolSize() == 0 ? 0D : (double) executor.getActiveCount() / executor.getMaximumPoolSize());
        snapshot.setQueueUsageRatio(queueCapacity == 0 ? 0D : (double) queueSize / queueCapacity);
        snapshot.setCompletedTaskCount(executor.getCompletedTaskCount());
        snapshot.setTaskCount(executor.getTaskCount());
        snapshot.setShutdown(executor.isShutdown());
        snapshot.setTerminating(executor.isTerminating());
        snapshot.setTerminated(executor.isTerminated());
        snapshot.setRejectedExecutionHandler(executor.getRejectedExecutionHandler().getClass().getSimpleName());
        return snapshot;
    }
}
