package com.xjtu.iron.concurrency.demo;

import com.xjtu.iron.concurrency.api.execution.executor.AsyncExecutor;
import com.xjtu.iron.concurrency.api.execution.task.AsyncTask;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AsyncExecutor 使用示例。
 *
 * <p>这个类每个接口对应 AsyncExecutor 的一个 API，方便你逐个验证：</p>
 * <ul>
 *     <li>execute：只投递，不关心结果。</li>
 *     <li>tryExecute：尝试投递，被拒绝返回 false。</li>
 *     <li>run：无业务返回值，但可以感知完成。</li>
 *     <li>supply：有业务返回值。</li>
 *     <li>submit：完整异步任务模型，支持 timeout、fallback、上下文控制。</li>
 * </ul>
 */
@RestController
@RequestMapping("/demo/async-executor")
public class AsyncExecutorDemoController {

    /**
     * 业务默认线程池名称。
     */
    private static final String DEFAULT_EXECUTOR = "default";

    /**
     * 业务查询线程池名称。
     */
    private static final String BIZ_QUERY_EXECUTOR = "biz-query-pool";

    /**
     * 异步执行器。
     */
    private final AsyncExecutor asyncExecutor;

    /**
     * 创建 AsyncExecutor 示例 Controller。
     *
     * @param asyncExecutor 异步执行器
     */
    public AsyncExecutorDemoController(AsyncExecutor asyncExecutor) {
        this.asyncExecutor = asyncExecutor;
    }

    /**
     * execute 示例：只把任务丢进线程池，不等待结果。
     *
     * @return 提交结果
     */
    @GetMapping("/execute")
    public Map<String, Object> execute() {
        asyncExecutor.execute(DEFAULT_EXECUTOR, "demoExecute", () -> sleep(100));

        return response(
                "api", "AsyncExecutor.execute",
                "meaning", "只提交任务，不关心任务返回值和完成状态",
                "result", "submitted"
        );
    }

    /**
     * tryExecute 示例：提交成功返回 true，被线程池拒绝返回 false。
     *
     * @return 是否成功提交
     */
    @GetMapping("/try-execute")
    public Map<String, Object> tryExecute() {
        boolean accepted = asyncExecutor.tryExecute(DEFAULT_EXECUTOR, "demoTryExecute", () -> sleep(100));

        return response(
                "api", "AsyncExecutor.tryExecute",
                "meaning", "适合可降级任务，拒绝时不抛异常，而是返回 false",
                "accepted", accepted
        );
    }

    /**
     * run 示例：任务没有业务返回值，但返回 CompletableFuture<Void> 表示完成状态。
     *
     * @return 任务完成结果
     */
    @GetMapping("/run")
    public Map<String, Object> run() {
        CompletableFuture<Void> future = asyncExecutor.run(DEFAULT_EXECUTOR, "demoRun", () -> sleep(100));
        future.join();

        return response(
                "api", "AsyncExecutor.run",
                "meaning", "没有业务返回值，但可以通过 CompletableFuture<Void> 感知成功、失败、完成",
                "done", future.isDone(),
                "completedExceptionally", future.isCompletedExceptionally()
        );
    }

    /**
     * supply 示例：提交有返回值的异步任务。
     *
     * @return 异步任务返回值
     */
    @GetMapping("/supply")
    public Map<String, Object> supply() {
        CompletableFuture<String> future = asyncExecutor.supply(BIZ_QUERY_EXECUTOR, "demoSupply", () -> "user:10001");

        return response(
                "api", "AsyncExecutor.supply",
                "meaning", "有业务返回值，返回 CompletableFuture<T>",
                "value", future.join()
        );
    }

    /**
     * submit 示例：完整异步任务模型，支持 timeout 和 fallback。
     *
     * @return submit 执行结果
     */
    @GetMapping("/submit")
    public Map<String, Object> submit() {
        CompletableFuture<String> future = asyncExecutor.submit(
                AsyncTask.of(BIZ_QUERY_EXECUTOR, "demoSubmit", () -> {
                            sleep(300);
                            return "real-value";
                        })
                        .timeout(Duration.ofMillis(100))
                        .fallback(ex -> "fallback-value")
                        .contextPropagation(true)
        );

        return response(
                "api", "AsyncExecutor.submit",
                "meaning", "完整异步任务模型，适合 timeout、fallback、上下文传播控制",
                "value", future.join()
        );
    }

    /**
     * 构建统一响应。
     *
     * @param keyValues key/value 交替数组
     * @return 响应 Map
     */
    private Map<String, Object> response(Object... keyValues) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return result;
    }

    /**
     * 模拟业务耗时。
     *
     * @param millis 睡眠毫秒数
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", ex);
        }
    }
}
