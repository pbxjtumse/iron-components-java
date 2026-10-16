package com.xjtu.iron.concurrency.demo;

import com.xjtu.iron.concurrency.api.execution.executor.AsyncExecutor;
import com.xjtu.iron.concurrency.api.execution.pool.ThreadPoolManager;
import com.xjtu.iron.concurrency.api.execution.pool.ThreadPoolSnapshot;
import com.xjtu.iron.concurrency.api.execution.registry.TaskExecutionRegistry;
import com.xjtu.iron.concurrency.api.execution.registry.TaskExecutionSnapshot;
import com.xjtu.iron.concurrency.api.execution.task.AsyncTask;
import com.xjtu.iron.concurrency.api.execution.template.AsyncTemplate;
import com.xjtu.iron.concurrency.api.retry.RetryPolicy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 一期增强能力演示 Controller。
 */
@RestController
@RequestMapping("/demo/phase1")
public class Phase1EnhancedDemoController {

    private final AsyncExecutor asyncExecutor;
    private final AsyncTemplate asyncTemplate;
    private final ThreadPoolManager threadPoolManager;
    private final TaskExecutionRegistry taskExecutionRegistry;

    public Phase1EnhancedDemoController(
            AsyncExecutor asyncExecutor,
            AsyncTemplate asyncTemplate,
            ThreadPoolManager threadPoolManager,
            TaskExecutionRegistry taskExecutionRegistry
    ) {
        this.asyncExecutor = asyncExecutor;
        this.asyncTemplate = asyncTemplate;
        this.threadPoolManager = threadPoolManager;
        this.taskExecutionRegistry = taskExecutionRegistry;
    }

    /**
     * 任务元数据增强示例。
     */
    @GetMapping("/metadata")
    public String metadata() {
        String taskId = "task-" + UUID.randomUUID();
        return asyncExecutor.submit(
                AsyncTask.of("biz-query-pool", "queryUserProfile", () -> "profile-result")
                        .taskId(taskId)
                        .bizKey("userId=10001")
                        .description("查询用户画像信息")
                        .tag("scene", "profile")
                        .tag("source", "demo")
                        .timeout(Duration.ofSeconds(2))
                        .queueTimeout(Duration.ofMillis(500))
                        .retryPolicy(RetryPolicy.fast(2, Duration.ofMillis(50)))
                        .fallback(ex -> "fallback-result")
        ).join();
    }

    /**
     * anySuccess 示例：第一个成功结果返回，失败任务会被忽略。
     */
    @GetMapping("/any-success")
    public String anySuccess() {
        CompletableFuture<String> failed = asyncExecutor.supply("biz-query-pool", "failedSource", () -> {
            throw new IllegalStateException("source failed");
        });
        CompletableFuture<String> success = asyncExecutor.supply("biz-query-pool", "successSource", () -> "success-value");
        return asyncTemplate.anySuccess(List.of(failed, success)).join();
    }

    /**
     * fire-and-forget 异常处理器示例。
     */
    @GetMapping("/fire-and-forget-error")
    public String fireAndForgetError() {
        asyncExecutor.execute("biz-query-pool", "fireAndForgetError", () -> {
            throw new IllegalStateException("fire-and-forget failed");
        });
        return "submitted";
    }

    /**
     * 线程池快照示例。
     */
    @GetMapping("/thread-pool")
    public ThreadPoolSnapshot threadPool(@RequestParam(defaultValue = "biz-query-pool") String executorName) {
        return threadPoolManager.snapshot(executorName);
    }

    /**
     * 最近任务状态示例。
     */
    @GetMapping("/tasks")
    public List<TaskExecutionSnapshot> tasks(@RequestParam(defaultValue = "20") int limit) {
        return taskExecutionRegistry.recent(limit);
    }


}
