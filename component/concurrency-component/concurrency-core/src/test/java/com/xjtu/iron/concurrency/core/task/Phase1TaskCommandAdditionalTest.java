package com.xjtu.iron.concurrency.core.task;

import com.xjtu.iron.concurrency.api.enums.error.AsyncErrorStage;
import com.xjtu.iron.concurrency.api.enums.task.AsyncTaskStatus;
import com.xjtu.iron.concurrency.api.exception.AsyncTaskException;
import com.xjtu.iron.concurrency.api.task.TaskResultMode;
import com.xjtu.iron.concurrency.core.testfixture.Phase1TestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase1 - TaskCommand 补充测试")
class Phase1TaskCommandAdditionalTest {

    @Test
    @DisplayName("SUCCESS 后 timeout 不能覆盖成功结果")
    void completeTimeoutShouldReturnFalseWhenAlreadySuccess() throws Exception {
        var fixture = Phase1TestSupport.commandFixture("cmd-success-timeout", () -> "ok", TaskResultMode.RESULT_AWARE);
        fixture.command().submitted();
        fixture.command().run();

        assertFalse(fixture.command().completeTimeout(new TimeoutException("late"), AsyncErrorStage.WAIT_RESULT));

        assertEquals("ok", fixture.baseFuture().get());
        assertEquals(AsyncTaskStatus.SUCCESS, fixture.runtime().getStatus());
        assertEquals(0, fixture.publisher().events().stream()
                .filter(event -> event.getStatus() == AsyncTaskStatus.TIMEOUT)
                .count());
    }

    @Test
    @DisplayName("queueTimeout 超过后不执行 operation，并把 baseFuture 异常完成")
    void runShouldNotExecuteWhenQueueTimeoutExceeded() throws Exception {
        AtomicBoolean executed = new AtomicBoolean(false);
        var task = com.xjtu.iron.concurrency.api.execution.task.AsyncTask
                .of("test-pool", "queue-timeout", () -> {
                    executed.set(true);
                    return "should-not-run";
                })
                .taskId("cmd-queue-timeout")
                .queueTimeout(Duration.ofMillis(1));
        task.validate();
        TaskDefinition<String> definition = TaskDefinition.from(task);
        var fixture = Phase1TestSupport.commandFixture("unused", () -> "unused", TaskResultMode.RESULT_AWARE);
        TaskExecutionContext<String> context = new TaskExecutionContext<>(
                definition,
                definition.getOperation(),
                fixture.baseFuture(),
                fixture.runtime()
        );
        TaskCommand<String> command = new TaskCommand<>(context, fixture.publisher(), fixture.classifier(), fixture.uncaught());
        command.submitted();
        Thread.sleep(15);

        command.run();

        assertFalse(executed.get());
        assertEquals(AsyncTaskStatus.TIMEOUT, context.getRuntime().getStatus());
        assertTrue(fixture.baseFuture().isCompletedExceptionally());
    }

    @Test
    @DisplayName("reject 应幂等，重复拒绝只发布一次 REJECTED")
    void rejectShouldBeIdempotent() {
        var fixture = Phase1TestSupport.commandFixture("cmd-reject", () -> "ok", TaskResultMode.RESULT_AWARE);
        fixture.command().submitted();

        fixture.command().reject(new RejectedExecutionException("full"));
        fixture.command().reject(new RejectedExecutionException("again"));

        assertEquals(AsyncTaskStatus.REJECTED, fixture.runtime().getStatus());
        assertTrue(fixture.baseFuture().isCompletedExceptionally());
        long rejectedCount = fixture.publisher().events().stream()
                .filter(event -> event.getStatus() == AsyncTaskStatus.REJECTED)
                .count();
        assertEquals(1, rejectedCount);
    }

    @Test
    @DisplayName("FIRE_AND_FORGET 失败时应进入 uncaught handler")
    void fireAndForgetFailureShouldCallUncaughtHandler() {
        var fixture = Phase1TestSupport.commandFixture(
                "cmd-fire-and-forget",
                () -> { throw new IllegalStateException("boom"); },
                TaskResultMode.FIRE_AND_FORGET
        );
        fixture.command().submitted();

        fixture.command().run();

        assertEquals(AsyncTaskStatus.FAILED, fixture.runtime().getStatus());
        assertEquals(1, fixture.uncaught().throwables().size());
        assertInstanceOf(AsyncTaskException.class, fixture.uncaught().throwables().get(0));
    }

    @Test
    @DisplayName("abortOnShutdown 应把 pending command 收口为 CANCELLED")
    void abortOnShutdownShouldCompleteCancelled() {
        var fixture = Phase1TestSupport.commandFixture("cmd-shutdown", () -> "ok", TaskResultMode.RESULT_AWARE);
        fixture.command().submitted();

        fixture.command().abortOnShutdown(new RuntimeException("shutdown"));

        assertEquals(AsyncTaskStatus.CANCELLED, fixture.runtime().getStatus());
        assertTrue(fixture.baseFuture().isCancelled());
    }


}
