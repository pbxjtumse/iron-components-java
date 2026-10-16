package com.xjtu.iron.concurrency.demo;

import com.xjtu.iron.concurrency.api.execution.executor.AsyncExecutor;
import com.xjtu.iron.concurrency.api.execution.registry.TaskExecutionRegistry;
import com.xjtu.iron.concurrency.api.execution.task.AsyncTask;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;

/**
 * 一期任务状态覆盖示例。
 *
 * <p>
 * 覆盖 SUCCESS、FAILED、TIMEOUT、FALLBACK_SUCCESS、FALLBACK_FAILED 和 REJECTED。
 * CANCELLED 需要后续 TaskHandle/cancel(taskId) API 才能形成完整可控链路，因此当前不伪造。
 * </p>
 */
@RestController
@RequestMapping("/demo/task-status")
public final class TaskStatusCoverageController {

    /**
     * 异步任务提交入口。
     */
    private final AsyncExecutor asyncExecutor;

    /**
     * 任务状态注册表。
     */
    private final TaskExecutionRegistry taskExecutionRegistry;

    public TaskStatusCoverageController(
            AsyncExecutor asyncExecutor,
            TaskExecutionRegistry taskExecutionRegistry
    ) {
        this.asyncExecutor = asyncExecutor;
        this.taskExecutionRegistry = taskExecutionRegistry;
    }

    /**
     * 正常成功状态。
     */
    @GetMapping("/success")
    public Map<String, Object> success() {
        String taskId = taskId("success");
        String value = asyncExecutor.submit(
                AsyncTask.of("default", "statusSuccess", () -> "success-value")
                        .taskId(taskId)
        ).join();
        return result(taskId, value);
    }

    /**
     * 原始任务执行失败状态。
     */
    @GetMapping("/failed")
    public Map<String, Object> failed() {
        String taskId = taskId("failed");
        try {
            asyncExecutor.submit(
                    AsyncTask.<String>of("default", "statusFailed", () -> {
                                throw new IllegalStateException("mock task failure");
                            })
                            .taskId(taskId)
            ).join();
        } catch (CompletionException ignored) {
            // 通过任务快照观察 FAILED 和结构化 AsyncError。
        }
        return result(taskId, null);
    }

    /**
     * 结果层超时状态。
     */
    @GetMapping("/timeout")
    public Map<String, Object> timeout() {
        String taskId = taskId("timeout");
        try {
            asyncExecutor.submit(
                    AsyncTask.of("default", "statusTimeout", () -> {
                                sleep(500L);
                                return "late-value";
                            })
                            .taskId(taskId)
                            .timeout(Duration.ofMillis(50L))
                            .cancelOnTimeout(true)
                            .interruptOnCancel(true)
            ).join();
        } catch (CompletionException ignored) {
            // 通过任务快照观察 TIMEOUT。
        }
        return result(taskId, null);
    }

    /**
     * 原始任务失败但 fallback 成功。
     */
    @GetMapping("/fallback-success")
    public Map<String, Object> fallbackSuccess() {
        String taskId = taskId("fallback-success");
        String value = asyncExecutor.submit(
                AsyncTask.<String>of("default", "statusFallbackSuccess", () -> {
                            throw new IllegalStateException("origin failure");
                        })
                        .taskId(taskId)
                        .fallback(throwable -> "fallback-value")
        ).join();
        return result(taskId, value);
    }

    /**
     * 原始任务失败并且 fallback 也失败。
     */
    @GetMapping("/fallback-failed")
    public Map<String, Object> fallbackFailed() {
        String taskId = taskId("fallback-failed");
        try {
            asyncExecutor.submit(
                    AsyncTask.<String>of("default", "statusFallbackFailed", () -> {
                                throw new IllegalStateException("origin failure");
                            })
                            .taskId(taskId)
                            .fallback(throwable -> {
                                throw new IllegalArgumentException("fallback failure");
                            })
            ).join();
        } catch (CompletionException ignored) {
            // 通过任务快照观察 FALLBACK_FAILED。
        }
        return result(taskId, null);
    }

    /**
     * 通过 tiny-pool 制造线程池拒绝。
     */
    @GetMapping("/rejected")
    public Map<String, Object> rejected() {
        String targetTaskId = taskId("rejected");

        /*
         * 先用长任务占满 tiny-pool 的工作线程和队列，
         * 再提交目标任务以触发 ABORT 拒绝策略。
         */
        asyncExecutor.supply("tiny-pool", "occupyWorker", () -> {
            sleep(1_000L);
            return "worker";
        });
        asyncExecutor.supply("tiny-pool", "occupyQueue", () -> {
            sleep(1_000L);
            return "queue";
        });

        try {
            asyncExecutor.submit(
                    AsyncTask.of("tiny-pool", "statusRejected", () -> "never-run")
                            .taskId(targetTaskId)
            ).join();
        } catch (CompletionException ignored) {
            // 通过任务快照观察 REJECTED。
        }

        return result(targetTaskId, null);
    }

    /**
     * 生成 Demo 任务 ID。
     */
    private String taskId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    /**
     * 返回任务结果与当前最新状态。
     */
    private Map<String, Object> result(String taskId, Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("value", value);
        result.put("snapshot", taskExecutionRegistry.get(taskId).orElse(null));
        return result;
    }

    /**
     * Demo 睡眠工具。
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
