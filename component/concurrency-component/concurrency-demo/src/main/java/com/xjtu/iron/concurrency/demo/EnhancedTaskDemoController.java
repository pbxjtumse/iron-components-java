package com.xjtu.iron.concurrency.demo;

import com.xjtu.iron.concurrency.api.execution.executor.AsyncExecutor;
import com.xjtu.iron.concurrency.api.execution.task.AsyncTask;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * 一期增强能力 Demo。
 *
 * <p>覆盖任务元数据、任务监听器、排队耗时、执行耗时、fire-and-forget 异常处理器。</p>
 */
@RestController
@RequestMapping("/demo/enhanced")
public class EnhancedTaskDemoController {

    /**
     * 业务查询线程池。
     */
    private static final String BIZ_QUERY_EXECUTOR = "biz-query-pool";

    /**
     * 小线程池，用于演示排队超时和拒绝。
     */
    private static final String TINY_EXECUTOR = "tiny-pool";

    /**
     * 异步执行器。
     */
    private final AsyncExecutor asyncExecutor;

    /**
     * Demo 事件记录器。
     */
    private final InMemoryTaskEventRecorder eventRecorder;

    /**
     * 创建增强能力 Demo Controller。
     *
     * @param asyncExecutor 异步执行器
     * @param eventRecorder 任务事件记录器
     */
    public EnhancedTaskDemoController(
            AsyncExecutor asyncExecutor,
            InMemoryTaskEventRecorder eventRecorder
    ) {
        this.asyncExecutor = asyncExecutor;
        this.eventRecorder = eventRecorder;
    }

    /**
     * 任务元数据增强示例。
     *
     * @return 任务结果和最近事件
     */
    @GetMapping("/metadata")
    public Map<String, Object> metadata() {
        String userId = "10001";
        CompletableFuture<String> future = asyncExecutor.submit(
                AsyncTask.of(BIZ_QUERY_EXECUTOR, "queryUserProfile", () -> "profile:" + userId)
                        .taskId("task-" + UUID.randomUUID())
                        .bizKey("userId=" + userId)
                        .description("查询用户画像信息")
                        .tag("scene", "user-profile")
                        .tag("source", "demo")
                        .timeout(Duration.ofSeconds(2))
                        .contextPropagation(true)
        );

        return response(
                "api", "AsyncTask metadata",
                "value", future.join(),
                "events", eventRecorder.recentEvents()
        );
    }

    /**
     * 排队耗时和执行耗时分开统计示例。
     *
     * <p>tiny-pool 只有 1 个线程和 1 个队列容量。第一个任务占住工作线程，第二个任务会进入队列。</p>
     *
     * @return 第二个任务执行结果和事件中的 queueCostMillis/runCostMillis
     */
    @GetMapping("/queue-cost")
    public Map<String, Object> queueCost() {
        eventRecorder.clear();

        asyncExecutor.run(TINY_EXECUTOR, "occupyTinyPool", () -> sleep(300));

        CompletableFuture<String> future = asyncExecutor.submit(
                AsyncTask.of(TINY_EXECUTOR, "queuedTask", () -> {
                            sleep(50);
                            return "queued-task-finished";
                        })
                        .bizKey("demo=queue-cost")
                        .tag("scene", "queue-cost")
        );

        return response(
                "api", "queueCost/runCost",
                "value", future.join(),
                "events", eventRecorder.recentEvents()
        );
    }

    /**
     * 排队超时示例。
     *
     * @return 排队超时结果
     */
    @GetMapping("/queue-timeout")
    public Map<String, Object> queueTimeout() {
        eventRecorder.clear();

        asyncExecutor.run(TINY_EXECUTOR, "occupyTinyPoolForTimeout", () -> sleep(300));

        CompletableFuture<String> future = asyncExecutor.submit(
                AsyncTask.of(TINY_EXECUTOR, "queueTimeoutTask", () -> "should-not-run")
                        .queueTimeout(Duration.ofMillis(50))
                        .bizKey("demo=queue-timeout")
                        .tag("scene", "queue-timeout")
                        .fallback(ex -> "fallback-after-queue-timeout")
        );

        return response(
                "api", "AsyncTask.queueTimeout",
                "value", future.join(),
                "events", eventRecorder.recentEvents()
        );
    }

    /**
     * fire-and-forget 异常处理器示例。
     *
     * @return 最近未捕获异步异常
     */
    @GetMapping("/fire-and-forget-exception")
    public Map<String, Object> fireAndForgetException() {
        eventRecorder.clear();

        asyncExecutor.execute(BIZ_QUERY_EXECUTOR, "fireAndForgetFailure", () -> {
            throw new IllegalStateException("fire-and-forget error");
        });

        sleep(100);

        return response(
                "api", "AsyncUncaughtExceptionHandler",
                "meaning", "execute 不返回 Future，异常会进入 AsyncUncaughtExceptionHandler",
                "uncaughtExceptions", eventRecorder.recentUncaughtExceptions(),
                "events", eventRecorder.recentEvents()
        );
    }

    /**
     * 结果层超时、取消与中断示例。
     *
     * @return 超时后的 fallback 结果
     */
    @GetMapping("/timeout-cancel")
    public Map<String, Object> timeoutCancel() {
        eventRecorder.clear();

        CompletableFuture<String> future = asyncExecutor.submit(
                AsyncTask.of(BIZ_QUERY_EXECUTOR, "timeoutCancelTask", () -> {
                            sleep(1000);
                            return "slow-value";
                        })
                        .timeout(Duration.ofMillis(100))
                        .cancelOnTimeout(true)
                        .interruptOnCancel(true)
                        .fallback(ex -> "fallback-after-timeout")
                        .bizKey("demo=timeout-cancel")
                        .tag("scene", "timeout-cancel")
        );

        try {
            return response(
                    "api", "timeout + cancelOnTimeout + fallback",
                    "value", future.join(),
                    "events", eventRecorder.recentEvents()
            );
        } catch (CompletionException ex) {
            return response(
                    "api", "timeout + cancelOnTimeout",
                    "error", ex.getMessage(),
                    "events", eventRecorder.recentEvents()
            );
        }
    }

    /**
     * 查看最近任务事件。
     *
     * @return 最近任务事件
     */
    @GetMapping("/events")
    public Map<String, Object> events() {
        return response(
                "events", eventRecorder.recentEvents(),
                "uncaughtExceptions", eventRecorder.recentUncaughtExceptions()
        );
    }

    private Map<String, Object> response(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return result;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", ex);
        }
    }
}
