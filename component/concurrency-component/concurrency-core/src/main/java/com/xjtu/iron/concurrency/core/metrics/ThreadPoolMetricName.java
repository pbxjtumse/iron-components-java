package com.xjtu.iron.concurrency.core.metrics;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.ToDoubleFunction;

/**
 * 线程池 Gauge 指标名称枚举。
 */
public enum ThreadPoolMetricName {

    ACTIVE_COUNT("xjtu.iron.concurrency.thread.pool.active", "Active thread count", ThreadPoolExecutor::getActiveCount),
    POOL_SIZE("xjtu.iron.concurrency.thread.pool.size", "Current thread pool size", ThreadPoolExecutor::getPoolSize),
    CORE_SIZE("xjtu.iron.concurrency.thread.pool.core.size", "Core pool size", ThreadPoolExecutor::getCorePoolSize),
    MAX_SIZE("xjtu.iron.concurrency.thread.pool.max.size", "Maximum pool size", ThreadPoolExecutor::getMaximumPoolSize),
    QUEUE_SIZE("xjtu.iron.concurrency.thread.pool.queue.size", "Current queue size", executor -> executor.getQueue().size()),
    QUEUE_REMAINING("xjtu.iron.concurrency.thread.pool.queue.remaining", "Queue remaining capacity", executor -> executor.getQueue().remainingCapacity()),
    COMPLETED_COUNT("xjtu.iron.concurrency.thread.pool.completed", "Completed task count", ThreadPoolExecutor::getCompletedTaskCount),
    TASK_COUNT("xjtu.iron.concurrency.thread.pool.task.count", "Total task count", ThreadPoolExecutor::getTaskCount);

    private final String meterName;
    private final String description;
    private final ToDoubleFunction<ThreadPoolExecutor> valueFunction;

    ThreadPoolMetricName(String meterName, String description, ToDoubleFunction<ThreadPoolExecutor> valueFunction) {
        this.meterName = meterName;
        this.description = description;
        this.valueFunction = valueFunction;
    }

    public String meterName() {
        return meterName;
    }

    public String description() {
        return description;
    }

    public double value(ThreadPoolExecutor executor) {
        return valueFunction.applyAsDouble(executor);
    }
}
