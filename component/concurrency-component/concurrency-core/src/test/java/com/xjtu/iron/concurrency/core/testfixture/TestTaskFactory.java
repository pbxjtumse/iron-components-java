package com.xjtu.iron.concurrency.core.testfixture;

import com.xjtu.iron.concurrency.api.execution.task.AsyncTask;
import com.xjtu.iron.concurrency.core.task.TaskDefinition;

import java.util.function.Supplier;

public final class TestTaskFactory {

    private TestTaskFactory() {
    }

    public static <T> AsyncTask<T> task(String taskId, Supplier<T> supplier) {
        AsyncTask<T> task = AsyncTask.of("test-pool", "test-task", supplier)
                .taskId(taskId)
                .bizKey("biz-" + taskId)
                .tag("scene", "phase1-test");

        task.validate();
        return task;
    }

    public static AsyncTask<String> successTask(String taskId, String value) {
        return task(taskId, () -> value);
    }

    public static AsyncTask<String> failureTask(String taskId, RuntimeException exception) {
        return task(taskId, () -> {
            throw exception;
        });
    }

    public static AsyncTask<Void> runnableTask(String taskId, Runnable runnable) {
        return task(taskId, () -> {
            runnable.run();
            return null;
        });
    }

    public static <T> TaskDefinition<T> definition(String taskId, Supplier<T> supplier) {
        return TaskDefinition.from(task(taskId, supplier));
    }
}
