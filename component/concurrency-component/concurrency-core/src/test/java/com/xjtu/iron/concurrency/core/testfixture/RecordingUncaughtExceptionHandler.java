package com.xjtu.iron.concurrency.core.testfixture;

import com.xjtu.iron.concurrency.api.event.TaskExecutionEvent;
import com.xjtu.iron.concurrency.api.listener.AsyncUncaughtExceptionHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RecordingUncaughtExceptionHandler
        implements AsyncUncaughtExceptionHandler {

    private final List<Throwable> throwables =
            new CopyOnWriteArrayList<>();

    private final List<TaskExecutionEvent> events =
            new CopyOnWriteArrayList<>();

    @Override
    public void handleException(TaskExecutionEvent event, Throwable throwable) {
        events.add(event.copy());
        throwables.add(throwable);
    }

    public List<Throwable> throwables() {
        return new ArrayList<>(throwables);
    }

    public List<TaskExecutionEvent> events() {
        return new ArrayList<>(events);
    }

    public Throwable firstThrowable() {
        List<Throwable> copy = throwables();
        if (copy.isEmpty()) {
            throw new AssertionError("No uncaught throwable recorded");
        }
        return copy.get(0);
    }
}
