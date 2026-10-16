package com.xjtu.iron.concurrency.demo;

import com.xjtu.iron.concurrency.api.event.TaskExecutionEvent;
import com.xjtu.iron.concurrency.api.listener.AsyncUncaughtExceptionHandler;
import com.xjtu.iron.concurrency.api.listener.TaskExecutionListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Demo 用任务事件记录器。
 *
 * <p>它同时演示两个扩展点：</p>
 * <ul>
 *     <li>{@link TaskExecutionListener}：监听 run/supply/submit/execute 的生命周期事件。</li>
 *     <li>{@link AsyncUncaughtExceptionHandler}：处理 execute 这种 fire-and-forget 任务的异常。</li>
 * </ul>
 */
@Component
public class InMemoryTaskEventRecorder implements TaskExecutionListener, AsyncUncaughtExceptionHandler {

    /**
     * 最多保留多少条事件，避免 Demo 内存无限增长。
     */
    private static final int MAX_SIZE = 100;

    /**
     * 任务事件列表。
     */
    private final List<Map<String, Object>> events = new ArrayList<>();

    /**
     * fire-and-forget 异常列表。
     */
    private final List<Map<String, Object>> uncaughtExceptions = new ArrayList<>();

    @Override
    public void onSubmitted(TaskExecutionEvent event) {
        addEvent("onSubmitted", event);
    }

    @Override
    public void onStarted(TaskExecutionEvent event) {
        addEvent("onStarted", event);
    }

    @Override
    public void onSuccess(TaskExecutionEvent event) {
        addEvent("onSuccess", event);
    }

    @Override
    public void onFailure(TaskExecutionEvent event) {
        addEvent("onFailure", event);
    }

    @Override
    public void onRejected(TaskExecutionEvent event) {
        addEvent("onRejected", event);
    }

    @Override
    public void onTimeout(TaskExecutionEvent event) {
        addEvent("onTimeout", event);
    }

    @Override
    public void onCancelled(TaskExecutionEvent event) {
        addEvent("onCancelled", event);
    }

    @Override
    public void onFallback(TaskExecutionEvent event) {
        addEvent("onFallback", event);
    }

    @Override
    public void onFallbackSuccess(TaskExecutionEvent event) {
        addEvent("onFallbackSuccess", event);
    }

    @Override
    public void onFallbackFailure(TaskExecutionEvent event) {
        addEvent("onFallbackFailure", event);
    }

    @Override
    public void onCompleted(TaskExecutionEvent event) {
        addEvent("onCompleted", event);
    }

    @Override
    public void handleException(TaskExecutionEvent event, Throwable throwable) {
        Map<String, Object> row = toMap("uncaught", event);
        row.put("exception", throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        synchronized (uncaughtExceptions) {
            uncaughtExceptions.add(row);
            trim(uncaughtExceptions);
        }
    }

    /**
     * 查询最近任务事件。
     *
     * @return 最近任务事件
     */
    public List<Map<String, Object>> recentEvents() {
        synchronized (events) {
            return new ArrayList<>(events);
        }
    }

    /**
     * 查询最近 fire-and-forget 异常。
     *
     * @return 最近异常
     */
    public List<Map<String, Object>> recentUncaughtExceptions() {
        synchronized (uncaughtExceptions) {
            return new ArrayList<>(uncaughtExceptions);
        }
    }

    /**
     * 清理记录。
     */
    public void clear() {
        synchronized (events) {
            events.clear();
        }
        synchronized (uncaughtExceptions) {
            uncaughtExceptions.clear();
        }
    }

    private void addEvent(String phase, TaskExecutionEvent event) {
        synchronized (events) {
            events.add(toMap(phase, event));
            trim(events);
        }
    }

    private Map<String, Object> toMap(String phase, TaskExecutionEvent event) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("phase", phase);
        row.put("status", event.getStatus() == null ? null : event.getStatus().name());
        row.put("taskId", event.getTask().getTaskId());
        row.put("executorName", event.getTask().getExecutorName());
        row.put("taskName", event.getTask().getTaskName());
        row.put("bizKey", event.getTask().getBizKey());
        row.put("tags", event.getTask().getTags());
        row.put("queueCostMillis", event.getTiming().getQueueCostMillis());
        row.put("runCostMillis", event.getTiming().getRunCostMillis());
        row.put("totalCostMillis", event.getTiming().getTotalCostMillis());
        row.put("message", event.getMessage());
        row.put("error", event.getError());
        if (event.getError() != null
                && event.getError().getException() != null
                && event.getError().getException().getThrowable() != null) {
            Throwable throwable = event.getError().getException().getThrowable();
            row.put("throwable", throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
        return row;
    }

    private void trim(List<Map<String, Object>> list) {
        while (list.size() > MAX_SIZE) {
            list.remove(0);
        }
    }
}
