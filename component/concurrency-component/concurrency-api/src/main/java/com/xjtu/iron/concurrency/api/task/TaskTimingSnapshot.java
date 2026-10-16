package com.xjtu.iron.concurrency.api.task;


/**
 * 任务执行时间快照。
 *
 * <p>
 * 该对象只保存某一时刻的不可变时间数据，
 * 不包含运行线程、AtomicBoolean 等 core 内部状态。
 * </p>
 */
public final class TaskTimingSnapshot {

    /**
     * 任务提交时间戳，毫秒。
     */
    private final long submitTimeMillis;

    /**
     * 任务开始执行时间戳，毫秒。
     *
     * <p>
     * 任务尚未开始或提交阶段被拒绝时为 0。
     * </p>
     */
    private final long startTimeMillis;

    /**
     * 任务结束时间戳，毫秒。
     *
     * <p>
     * 任务尚未结束时为 0。
     * </p>
     */
    private final long endTimeMillis;

    /**
     * 任务排队耗时，毫秒。
     */
    private final long queueCostMillis;

    /**
     * 任务实际执行耗时，毫秒。
     */
    private final long runCostMillis;

    /**
     * 从提交到当前或结束的总耗时，毫秒。
     */
    private final long totalCostMillis;

    public TaskTimingSnapshot(
            long submitTimeMillis,
            long startTimeMillis,
            long endTimeMillis,
            long queueCostMillis,
            long runCostMillis,
            long totalCostMillis
    ) {
        this.submitTimeMillis = submitTimeMillis;
        this.startTimeMillis = startTimeMillis;
        this.endTimeMillis = endTimeMillis;
        this.queueCostMillis = Math.max(0L, queueCostMillis);
        this.runCostMillis = Math.max(0L, runCostMillis);
        this.totalCostMillis = Math.max(0L, totalCostMillis);
    }

    public static TaskTimingSnapshot empty() {
        return new TaskTimingSnapshot(0, 0, 0, 0, 0, 0);
    }

    public long getSubmitTimeMillis() {
        return submitTimeMillis;
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public long getEndTimeMillis() {
        return endTimeMillis;
    }

    public long getQueueCostMillis() {
        return queueCostMillis;
    }

    public long getRunCostMillis() {
        return runCostMillis;
    }

    public long getTotalCostMillis() {
        return totalCostMillis;
    }
}
