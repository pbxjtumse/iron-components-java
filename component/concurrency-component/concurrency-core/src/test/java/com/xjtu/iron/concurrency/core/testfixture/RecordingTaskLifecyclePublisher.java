package com.xjtu.iron.concurrency.core.testfixture;

import com.xjtu.iron.concurrency.api.enums.task.AsyncTaskStatus;
import com.xjtu.iron.concurrency.api.event.TaskExecutionEvent;
import com.xjtu.iron.concurrency.core.lifecycle.TaskLifecyclePublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RecordingTaskLifecyclePublisher
        implements TaskLifecyclePublisher {

    private final List<TaskExecutionEvent> events =
            new CopyOnWriteArrayList<>();

    private final List<TaskExecutionEvent> completedEvents =
            new CopyOnWriteArrayList<>();

    @Override
    public void publish(TaskExecutionEvent event) {
        events.add(event.copy());
    }

    @Override
    public void publishCompleted(TaskExecutionEvent terminalEvent) {
        completedEvents.add(terminalEvent.copy());
    }

    public List<TaskExecutionEvent> events() {
        return new ArrayList<>(events);
    }

    public List<TaskExecutionEvent> completedEvents() {
        return new ArrayList<>(completedEvents);
    }

    public List<AsyncTaskStatus> statuses() {
        return events.stream()
                .map(TaskExecutionEvent::getStatus)
                .toList();
    }

    public long count(AsyncTaskStatus status) {
        return events.stream()
                .filter(event -> event.getStatus() == status)
                .count();
    }

    public boolean contains(AsyncTaskStatus status) {
        return events.stream()
                .anyMatch(event -> event.getStatus() == status);
    }

    public TaskExecutionEvent first(AsyncTaskStatus status) {
        return events.stream()
                .filter(event -> event.getStatus() == status)
                .findFirst()
                .orElseThrow();
    }
}
