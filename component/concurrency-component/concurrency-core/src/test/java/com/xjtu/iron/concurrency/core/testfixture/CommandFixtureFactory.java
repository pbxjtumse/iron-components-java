package com.xjtu.iron.concurrency.core.testfixture;

import com.xjtu.iron.concurrency.api.task.TaskResultMode;
import com.xjtu.iron.concurrency.core.task.TaskCommand;
import com.xjtu.iron.concurrency.core.task.TaskDefinition;
import com.xjtu.iron.concurrency.core.task.TaskExecutionContext;
import com.xjtu.iron.concurrency.core.task.TaskExecutionRuntime;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class CommandFixtureFactory {

    private CommandFixtureFactory() {
    }

    public static <T> CommandFixture<T> create(
            String taskId,
            Supplier<T> supplier,
            TaskResultMode resultMode
    ) {
        TaskDefinition<T> definition = TestTaskFactory.definition(taskId, supplier);

        CompletableFuture<T> baseFuture = new CompletableFuture<>();
        TaskExecutionRuntime runtime = new TaskExecutionRuntime(resultMode);

        TaskExecutionContext<T> context = new TaskExecutionContext<>(
                definition,
                definition.getOperation(),
                baseFuture,
                runtime
        );

        RecordingTaskLifecyclePublisher publisher =
                new RecordingTaskLifecyclePublisher();

        RecordingAsyncErrorClassifier classifier =
                new RecordingAsyncErrorClassifier();

        RecordingUncaughtExceptionHandler uncaught =
                new RecordingUncaughtExceptionHandler();

        TaskCommand<T> command = new TaskCommand<>(
                context,
                publisher,
                classifier,
                uncaught
        );

        return new CommandFixture<>(
                definition,
                context,
                baseFuture,
                runtime,
                command,
                publisher,
                classifier,
                uncaught
        );
    }
}