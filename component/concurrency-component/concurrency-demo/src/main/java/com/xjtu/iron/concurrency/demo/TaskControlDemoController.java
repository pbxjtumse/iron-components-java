package com.xjtu.iron.concurrency.demo;

import com.xjtu.iron.concurrency.api.execution.executor.AsyncExecutor;
import com.xjtu.iron.concurrency.api.execution.registry.TaskExecutionRegistry;
import com.xjtu.iron.concurrency.api.execution.task.AsyncTask;
import com.xjtu.iron.concurrency.api.execution.task.TaskCancelResult;
import com.xjtu.iron.concurrency.api.execution.task.TaskHandle;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 任务取消、CALLER_RUNS 和拒绝感知 DISCARD 的人工冒烟测试接口。
 *
 * <p>
 * 这些接口用于启动应用后的链路验证，不替代 core 层单元测试。
 * </p>
 */
@RestController
@RequestMapping("/demo/task-control")
public final class TaskControlDemoController {

    /** 异步执行入口。 */
    private final AsyncExecutor asyncExecutor;

    /** 任务状态查询入口。 */
    private final TaskExecutionRegistry taskExecutionRegistry;

    public TaskControlDemoController(
            AsyncExecutor asyncExecutor,
            TaskExecutionRegistry taskExecutionRegistry
    ) {
        this.asyncExecutor = asyncExecutor;
        this.taskExecutionRegistry = taskExecutionRegistry;
    }

    /**
     * 验证队列中任务能够被移除并标记为 CANCELLED。
     */
    @GetMapping("/cancel-queued")
    public Map<String, Object> cancelQueued() {
        asyncExecutor.supply("tiny-pool", "occupyWorkerForCancel", () -> {
            sleep(1_500L);
            return "worker-finished";
        });

        String taskId = "cancel-queued-" + UUID.randomUUID();
        TaskHandle<String> handle = asyncExecutor.submitHandle(
                AsyncTask.of("tiny-pool", "cancelQueuedTarget", () -> "should-not-run")
                        .taskId(taskId)
        );

        TaskCancelResult cancelResult = handle.cancel(false);
        return response(taskId, cancelResult, handle.getFuture().isCancelled(), null);
    }

    /**
     * 验证运行中任务取消和协作式 interrupt。
     */
    @GetMapping("/cancel-running")
    public Map<String, Object> cancelRunning() {
        String taskId = "cancel-running-" + UUID.randomUUID();
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<String> workerThread = new AtomicReference<>();

        TaskHandle<String> handle = asyncExecutor.submitHandle(
                AsyncTask.of("default", "cancelRunningTarget", () -> {
                            workerThread.set(Thread.currentThread().getName());
                            started.countDown();
                            try {
                                Thread.sleep(10_000L);
                                return "unexpected-finish";
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(
                                        "Task observed interrupt",
                                        interrupted
                                );
                            }
                        })
                        .taskId(taskId)
        );

        await(started, 1_000L);
        TaskCancelResult cancelResult = handle.cancel(true);
        return response(
                taskId,
                cancelResult,
                handle.getFuture().isCancelled(),
                workerThread.get()
        );
    }

    /**
     * 验证 CALLER_RUNS 在提交线程执行，并通过 executionMode=CALLER_THREAD 暴露。
     */
    @GetMapping("/caller-runs")
    public Map<String, Object> callerRuns() {
        asyncExecutor.supply("caller-runs-pool", "occupyCallerRunsWorker", () -> {
            sleep(1_000L);
            return "worker";
        });
        sleep(50L);
        asyncExecutor.supply("caller-runs-pool", "occupyCallerRunsQueue", () -> {
            sleep(1_000L);
            return "queue";
        });

        String taskId = "caller-runs-" + UUID.randomUUID();
        String submitThread = Thread.currentThread().getName();
        String executionThread = asyncExecutor.submit(
                AsyncTask.of(
                                "caller-runs-pool",
                                "callerRunsTarget",
                                () -> Thread.currentThread().getName()
                        )
                        .taskId(taskId)
        ).join();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("submitThread", submitThread);
        result.put("executionThread", executionThread);
        result.put("sameThread", submitThread.equals(executionThread));
        result.put("snapshot", taskExecutionRegistry.get(taskId).orElse(null));
        return result;
    }

    /**
     * 验证拒绝感知版 DISCARD：提交方法不同步抛异常，但 Future 异常完成且快照为 REJECTED。
     */
    @GetMapping("/discard")
    public Map<String, Object> discard() {
        asyncExecutor.supply("discard-pool", "occupyDiscardWorker", () -> {
            sleep(1_000L);
            return "worker";
        });
        sleep(50L);
        asyncExecutor.supply("discard-pool", "occupyDiscardQueue", () -> {
            sleep(1_000L);
            return "queue";
        });

        String taskId = "discard-" + UUID.randomUUID();
        boolean failedAsynchronously = false;

        try {
            asyncExecutor.submit(
                    AsyncTask.of("discard-pool", "discardTarget", () -> "never-run")
                            .taskId(taskId)
            ).join();
        } catch (CompletionException expected) {
            failedAsynchronously = true;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("futureFailed", failedAsynchronously);
        result.put("snapshot", taskExecutionRegistry.get(taskId).orElse(null));
        return result;
    }

    private Map<String, Object> response(
            String taskId,
            TaskCancelResult cancelResult,
            boolean futureCancelled,
            String workerThread
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("cancelResult", cancelResult);
        result.put("futureCancelled", futureCancelled);
        result.put("workerThread", workerThread);
        result.put("snapshot", taskExecutionRegistry.get(taskId).orElse(null));
        return result;
    }

    private void await(CountDownLatch latch, long timeoutMillis) {
        try {
            latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
