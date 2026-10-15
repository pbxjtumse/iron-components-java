package com.xjtu.iron.concurrency.core.metrics;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.ToDoubleFunction;

/**
 * 线程池 Gauge 指标名称枚举。
 */
public enum ThreadPoolMetricName {

    /** 当前正在执行任务的线程数。 */
    ACTIVE_COUNT("xjtu.iron.concurrency.thread.pool.active", "Active thread count", ThreadPoolExecutor::getActiveCount),

    /** 当前线程池中已创建的线程数。 */
    POOL_SIZE("xjtu.iron.concurrency.thread.pool.size", "Current thread pool size", ThreadPoolExecutor::getPoolSize),

    /** 线程池配置的核心线程数。 */
    CORE_SIZE("xjtu.iron.concurrency.thread.pool.core.size", "Core pool size", ThreadPoolExecutor::getCorePoolSize),

    /** 线程池允许的最大线程数。 */
    MAX_SIZE("xjtu.iron.concurrency.thread.pool.max.size", "Maximum pool size", ThreadPoolExecutor::getMaximumPoolSize),

    /** 当前在队列中等待执行的任务数。 */
    QUEUE_SIZE("xjtu.iron.concurrency.thread.pool.queue.size", "Current queue size", executor -> executor.getQueue().size()),

    /** 任务队列当前剩余的可用容量。 */
    QUEUE_REMAINING("xjtu.iron.concurrency.thread.pool.queue.remaining", "Queue remaining capacity",
        executor -> executor.getQueue().remainingCapacity()),

    /** 线程池历史上已完成的任务总数。 */
    COMPLETED_COUNT("xjtu.iron.concurrency.thread.pool.completed", "Completed task count", ThreadPoolExecutor::getCompletedTaskCount),

    /** 线程池历史上已提交的任务总数。 */
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
