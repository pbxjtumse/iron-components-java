package com.xjtu.iron.concurrency.core.testfixture;

import com.xjtu.iron.concurrency.api.execution.task.AsyncTask;
import com.xjtu.iron.concurrency.api.task.TaskResultMode;
import com.xjtu.iron.concurrency.core.task.TaskDefinition;

import java.util.function.Supplier;

/**
 * 一期测试公共入口。
 *
 * <p>这个类只做轻量门面，不再持有大量内部嵌套类。
 * 具体能力拆到 TestTaskFactory、CommandFixtureFactory、
 * RecordingTaskLifecyclePublisher、RecordingAsyncErrorClassifier 等独立类中。</p>
 */
public final class Phase1TestSupport {

    private Phase1TestSupport() {
    }

    public static <T> AsyncTask<T> task(String taskId, Supplier<T> supplier) {
        return TestTaskFactory.task(taskId, supplier);
    }

    public static <T> TaskDefinition<T> definition(String taskId, Supplier<T> supplier) {
        return TestTaskFactory.definition(taskId, supplier);
    }

    public static <T> CommandFixture<T> commandFixture(
            String taskId,
            Supplier<T> supplier,
            TaskResultMode resultMode
    ) {
        return CommandFixtureFactory.create(taskId, supplier, resultMode);
    }

    public static BlockingTask blockingTask() {
        return new BlockingTask();
    }
}