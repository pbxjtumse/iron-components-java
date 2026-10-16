package com.xjtu.iron.concurrency.core.lifecycle;

import com.xjtu.iron.concurrency.api.enums.task.AsyncTaskStatus;
import com.xjtu.iron.concurrency.api.event.TaskExecutionEvent;
import com.xjtu.iron.concurrency.api.execution.registry.TaskExecutionRegistry;
import com.xjtu.iron.concurrency.api.execution.registry.TaskExecutionSnapshot;
import com.xjtu.iron.concurrency.api.listener.TaskExecutionListener;
import com.xjtu.iron.concurrency.core.metrics.ConcurrencyMetricsRecorder;

import java.util.Objects;

/**
 * 默认任务生命周期发布器。
 *
 * <p>
 * 每次状态变化都会先写入指标、更新任务快照，再通知业务监听器。
 * 监听器通常已经由 CompositeTaskExecutionListener 组合并隔离异常。
 * </p>
 */
public final class DefaultTaskLifecyclePublisher implements TaskLifecyclePublisher {

    /**
     * 机器指标记录器。
     */
    private final ConcurrencyMetricsRecorder metricsRecorder;

    /**
     * 当前任务状态注册表。
     */
    private final TaskExecutionRegistry taskExecutionRegistry;

    /**
     * 组合后的业务任务监听器。
     */
    private final TaskExecutionListener taskExecutionListener;

    public DefaultTaskLifecyclePublisher(
            ConcurrencyMetricsRecorder metricsRecorder,
            TaskExecutionRegistry taskExecutionRegistry,
            TaskExecutionListener taskExecutionListener
    ) {
        this.metricsRecorder = Objects.requireNonNull(metricsRecorder, "metricsRecorder must not be null");
        this.taskExecutionRegistry = Objects.requireNonNull(taskExecutionRegistry, "taskExecutionRegistry must not be null");
        this.taskExecutionListener = Objects.requireNonNull(taskExecutionListener, "taskExecutionListener must not be null");
    }

    @Override
    public void publish(TaskExecutionEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        recordSpecificMetric(event);
        taskExecutionRegistry.update(TaskExecutionSnapshot.from(event));
        notifySpecificListener(event);
    }

    @Override
    public void publishCompleted(TaskExecutionEvent terminalEvent) {
        Objects.requireNonNull(terminalEvent, "terminalEvent must not be null");

        /*
         * completed 是终态旁路通知，不再重复写 Registry。
         * 具体终态事件 SUCCESS / FAILED / FALLBACK_SUCCESS 等已经在 publish(event) 中
         * 写入过快照；这里重复写会导致版本号和顺序索引无意义膨胀。
         */
        metricsRecorder.recordCompleted(terminalEvent.copy());
        taskExecutionListener.onCompleted(terminalEvent.copy());
    }

    /**
     * 根据事件状态记录对应指标。
     */
    private void recordSpecificMetric(TaskExecutionEvent event) {
        AsyncTaskStatus status = event.getStatus();

        switch (status) {
            case SUBMITTED -> metricsRecorder.recordSubmitted(event.copy());
            case RUNNING -> metricsRecorder.recordStarted(event.copy());
            case SUCCESS -> metricsRecorder.recordSuccess(event.copy());
            case FAILED -> metricsRecorder.recordFailure(event.copy());
            case REJECTED -> metricsRecorder.recordRejected(event.copy());
            case TIMEOUT -> metricsRecorder.recordTimeout(event.copy());
            case CANCELLED -> metricsRecorder.recordCancelled(event.copy());
            case FALLBACK -> metricsRecorder.recordFallback(event.copy());
            case FALLBACK_SUCCESS -> metricsRecorder.recordFallbackSuccess(event.copy());
            case FALLBACK_FAILED -> metricsRecorder.recordFallbackFailure(event.copy());
            case CREATED -> {
                // CREATED 是任务对象初始状态，当前不作为运行指标记录。
            }
        }
    }

    /**
     * 根据事件状态调用对应业务监听器。
     */
    private void notifySpecificListener(TaskExecutionEvent event) {
        AsyncTaskStatus status = event.getStatus();

        switch (status) {
            case SUBMITTED -> taskExecutionListener.onSubmitted(event.copy());
            case RUNNING -> taskExecutionListener.onStarted(event.copy());
            case SUCCESS -> taskExecutionListener.onSuccess(event.copy());
            case FAILED -> taskExecutionListener.onFailure(event.copy());
            case REJECTED -> taskExecutionListener.onRejected(event.copy());
            case TIMEOUT -> taskExecutionListener.onTimeout(event.copy());
            case CANCELLED -> taskExecutionListener.onCancelled(event.copy());
            case FALLBACK -> taskExecutionListener.onFallback(event.copy());
            case FALLBACK_SUCCESS -> taskExecutionListener.onFallbackSuccess(event.copy());
            case FALLBACK_FAILED -> taskExecutionListener.onFallbackFailure(event.copy());
            case CREATED -> {
                // CREATED 当前不对业务发布。
            }
        }
    }
}
