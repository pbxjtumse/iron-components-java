package com.xjtu.iron.concurrency.starter.observability;

import com.xjtu.iron.concurrency.api.event.TaskExecutionEvent;
import com.xjtu.iron.concurrency.core.metrics.ConcurrencyMetricsRecorder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Micrometer 的任务指标记录器。
 *
 * <p>
 * Counter 用于统计任务生命周期次数；Timer 用于记录排队、执行和总耗时。
 * 指标标签只使用线程池名、任务名和状态等低基数字段，不使用 taskId、bizKey。
 * </p>
 */
public final class MicrometerConcurrencyMetricsRecorder
        implements ConcurrencyMetricsRecorder {

    /**
     * Micrometer 指标注册中心。
     */
    private final MeterRegistry meterRegistry;

    public MicrometerConcurrencyMetricsRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(
                meterRegistry,
                "meterRegistry must not be null"
        );
    }

    @Override
    public void recordSubmitted(TaskExecutionEvent event) {
        counter("xjtu.iron.concurrency.task.submitted", event).increment();
    }

    @Override
    public void recordStarted(TaskExecutionEvent event) {
        counter("xjtu.iron.concurrency.task.started", event).increment();

        if (event.getExecutionMode()
                == com.xjtu.iron.concurrency.api.task.TaskExecutionMode.CALLER_THREAD) {
            counter("xjtu.iron.concurrency.task.caller.runs", event).increment();
        }
    }

    @Override
    public void recordSuccess(TaskExecutionEvent event) {
        counter("xjtu.iron.concurrency.task.success", event).increment();
        recordExecutionTimers(event);
    }

    @Override
    public void recordFailure(TaskExecutionEvent event) {
        counter("xjtu.iron.concurrency.task.failure", event).increment();
        recordExecutionTimers(event);
    }

    @Override
    public void recordRejected(TaskExecutionEvent event) {
        counter("xjtu.iron.concurrency.task.rejected", event).increment();
        recordTotalTimer(event);
    }

    @Override
    public void recordTimeout(TaskExecutionEvent event) {
        counter("xjtu.iron.concurrency.task.timeout", event).increment();
        recordTotalTimer(event);
    }

    @Override
    public void recordCancelled(TaskExecutionEvent event) {
        counter("xjtu.iron.concurrency.task.cancelled", event).increment();
        recordTotalTimer(event);
    }

    @Override
    public void recordFallback(TaskExecutionEvent event) {
        counter("xjtu.iron.concurrency.task.fallback", event).increment();
    }

    @Override
    public void recordFallbackSuccess(TaskExecutionEvent event) {
        counter("xjtu.iron.concurrency.task.fallback.success", event).increment();
        recordTotalTimer(event);
    }

    @Override
    public void recordFallbackFailure(TaskExecutionEvent event) {
        counter("xjtu.iron.concurrency.task.fallback.failure", event).increment();
        recordTotalTimer(event);
    }

    @Override
    public void recordCompleted(TaskExecutionEvent event) {
        counter("xjtu.iron.concurrency.task.completed", event).increment();
    }

    /**
     * 创建或获取 Counter。
     */
    private Counter counter(String name, TaskExecutionEvent event) {
        return Counter.builder(name)
                .tags(tags(event))
                .register(meterRegistry);
    }

    /**
     * 记录原始任务排队、运行和总耗时。
     */
    private void recordExecutionTimers(TaskExecutionEvent event) {
        timer("xjtu.iron.concurrency.task.queue.cost", event)
                .record(event.getTiming().getQueueCostMillis(), TimeUnit.MILLISECONDS);
        timer("xjtu.iron.concurrency.task.run.cost", event)
                .record(event.getTiming().getRunCostMillis(), TimeUnit.MILLISECONDS);
        recordTotalTimer(event);
    }

    /**
     * 记录从提交到当前终态的总耗时。
     */
    private void recordTotalTimer(TaskExecutionEvent event) {
        timer("xjtu.iron.concurrency.task.total.cost", event)
                .record(event.getTiming().getTotalCostMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * 创建或获取 Timer。
     */
    private Timer timer(String name, TaskExecutionEvent event) {
        return Timer.builder(name)
                .tags(tags(event))
                .register(meterRegistry);
    }

    /**
     * 构造低基数指标标签。
     */
    private Tags tags(TaskExecutionEvent event) {
        return Tags.of(
                "component", "xjtu-iron-concurrency",
                "executor", safe(event.getTask().getExecutorName()),
                "task", safe(event.getTask().getTaskName()),
                "status", event.getStatus().name(),
                "execution_mode", event.getExecutionMode().name()
        );
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
