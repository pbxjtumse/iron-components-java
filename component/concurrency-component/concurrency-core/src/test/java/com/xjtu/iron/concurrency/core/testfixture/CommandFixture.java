package com.xjtu.iron.concurrency.core.testfixture;


import com.xjtu.iron.concurrency.core.task.TaskCommand;
import com.xjtu.iron.concurrency.core.task.TaskDefinition;
import com.xjtu.iron.concurrency.core.task.TaskExecutionContext;
import com.xjtu.iron.concurrency.core.task.TaskExecutionRuntime;

import java.util.concurrent.CompletableFuture;

/**
 * TaskCommand 测试用白盒夹具。
 *
 * <p>一次 TaskCommand 测试通常不只需要 command 本身，
 * 还需要观察 runtime、baseFuture、publisher、classifier、uncaught 等协作对象。</p>
 */
public record CommandFixture<T>(
        TaskDefinition<T> definition,
        TaskExecutionContext<T> context,
        CompletableFuture<T> baseFuture,
        TaskExecutionRuntime runtime,
        TaskCommand<T> command,
        RecordingTaskLifecyclePublisher publisher,
        RecordingAsyncErrorClassifier classifier,
        RecordingUncaughtExceptionHandler uncaught
) {
}
