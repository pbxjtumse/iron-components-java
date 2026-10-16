package com.xjtu.iron.concurrency.core.testfixture;

import com.xjtu.iron.concurrency.api.enums.task.AsyncTaskStatus;
import com.xjtu.iron.concurrency.api.event.TaskExecutionEvent;
import com.xjtu.iron.concurrency.api.listener.TaskExecutionListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RecordingTaskExecutionListener
        implements TaskExecutionListener {

    private final List<TaskExecutionEvent> events =
            new CopyOnWriteArrayList<>();

    @Override
    public void onSubmitted(TaskExecutionEvent event) {
        events.add(event.copy());
    }

    @Override
    public void onStarted(TaskExecutionEvent event) {
        events.add(event.copy());
    }

    @Override
    public void onSuccess(TaskExecutionEvent event) {
        events.add(event.copy());
    }

    @Override
    public void onFailure(TaskExecutionEvent event) {
        events.add(event.copy());
    }

    @Override
    public void onRejected(TaskExecutionEvent event) {
        events.add(event.copy());
    }

    @Override
    public void onTimeout(TaskExecutionEvent event) {
        events.add(event.copy());
    }

    @Override
    public void onCancelled(TaskExecutionEvent event) {
        events.add(event.copy());
    }

    @Override
    public void onFallback(TaskExecutionEvent event) {
        events.add(event.copy());
    }

    @Override
    public void onFallbackSuccess(TaskExecutionEvent event) {
        events.add(event.copy());
    }

    @Override
    public void onFallbackFailure(TaskExecutionEvent event) {
        events.add(event.copy());
    }

    @Override
    public void onCompleted(TaskExecutionEvent event) {
        events.add(event.copy());
    }

    public List<TaskExecutionEvent> events() {
        return new ArrayList<>(events);
    }

    public List<AsyncTaskStatus> statuses() {
        return events.stream()
                .map(TaskExecutionEvent::getStatus)
                .toList();
    }
}
