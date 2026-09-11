package com.xjtu.iron.concurrency.demo;

import com.xjtu.iron.concurrency.api.execution.executor.AsyncExecutor;
import com.xjtu.iron.concurrency.api.execution.template.AsyncBatchResult;
import com.xjtu.iron.concurrency.api.execution.template.AsyncTaskOutcome;
import com.xjtu.iron.concurrency.api.execution.template.AsyncTemplate;
import com.xjtu.iron.concurrency.api.execution.template.NamedFuture;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

/**
 * AsyncTemplate 使用示例。
 *
 * <p>AsyncTemplate 不负责把任务提交到线程池，它只负责对 CompletableFuture 进行编排。</p>
 */
@RestController
@RequestMapping("/demo/async-template")
public class AsyncTemplateDemoController {

    /**
     * 业务查询线程池名称。
     */
    private static final String BIZ_QUERY_EXECUTOR = "biz-query-pool";

    /**
     * 异步执行器，用于生成 CompletableFuture。
     */
    private final AsyncExecutor asyncExecutor;

    /**
     * 异步编排模板。
     */
    private final AsyncTemplate asyncTemplate;

    /**
     * 创建 AsyncTemplate 示例 Controller。
     *
     * @param asyncExecutor 异步执行器
     * @param asyncTemplate 异步编排模板
     */
    public AsyncTemplateDemoController(AsyncExecutor asyncExecutor, AsyncTemplate asyncTemplate) {
        this.asyncExecutor = asyncExecutor;
        this.asyncTemplate = asyncTemplate;
    }

    /**
     * allOf 示例：所有任务都必须成功。
     *
     * @return 所有任务结果
     */
    @GetMapping("/all-of")
    public Map<String, Object> allOf() {
        CompletableFuture<String> userFuture = asyncExecutor.supply(BIZ_QUERY_EXECUTOR, "queryUser", () -> "user");
        CompletableFuture<String> accountFuture = asyncExecutor.supply(BIZ_QUERY_EXECUTOR, "queryAccount", () -> "account");
        //join() 会阻塞当前执行的线程
        List<String> values = asyncTemplate.allOf(List.of(userFuture, accountFuture)).join();

        return Map.of(
                "api", "AsyncTemplate.allOf",
                "meaning", "所有任务都成功才整体成功",
                "values", values
        );
    }

    /**
     * allOfOutcome 示例：允许部分成功，整体不因为单个任务失败而失败。
     *
     * @return 成功结果和失败任务
     */
    @GetMapping("/all-of-outcome")
    public Map<String, Object> allOfOutcome() {
        CompletableFuture<String> successFuture = asyncExecutor.supply(BIZ_QUERY_EXECUTOR, "queryUser", () -> "user");
        CompletableFuture<String> failureFuture = asyncExecutor.supply(BIZ_QUERY_EXECUTOR, "queryCoupon", () -> {
            throw new IllegalStateException("coupon service error");
        });

        AsyncBatchResult<String> result = asyncTemplate.allOfOutcome(List.of(
                NamedFuture.of("queryUser", successFuture),
                NamedFuture.of("queryCoupon", failureFuture)
        )).join();

        List<String> failureNames = result.failures()
                .stream()
                .map(AsyncTaskOutcome::getTaskName)
                .collect(Collectors.toList());

        return Map.of(
                "api", "AsyncTemplate.allOfOutcome",
                "meaning", "等待所有任务完成，成功失败都收集起来",
                "allSuccess", result.isAllSuccess(),
                "successValues", result.successValues(),
                "failures", failureNames
        );
    }

    /**
     * allOfFailFast 示例：任意一个任务失败，整体立即失败。
     *
     * @return fail-fast 结果
     */
    @GetMapping("/all-of-fail-fast")
    public Map<String, Object> allOfFailFast() {
        CompletableFuture<String> slowFuture = asyncExecutor.supply(BIZ_QUERY_EXECUTOR, "slowQuery", () -> {
            sleep(500);
            return "slow";
        });
        CompletableFuture<String> failureFuture = asyncExecutor.supply(BIZ_QUERY_EXECUTOR, "fastFailure", () -> {
            throw new IllegalStateException("fast failure");
        });

        try {
            asyncTemplate.allOfFailFast(List.of(slowFuture, failureFuture)).join();
            return Map.of("api", "AsyncTemplate.allOfFailFast", "success", true);
        } catch (CompletionException ex) {
            return Map.of(
                    "api", "AsyncTemplate.allOfFailFast",
                    "meaning", "任意一个失败，整体尽快失败",
                    "success", false,
                    "error", rootMessage(ex)
            );
        }
    }

    /**
     * anyOf 示例：谁先完成就返回谁。
     *
     * @return 最先完成的任务结果
     */
    @GetMapping("/any-of")
    public Map<String, Object> anyOf() {
        CompletableFuture<String> fastFuture = asyncExecutor.supply(BIZ_QUERY_EXECUTOR, "fastQuery", () -> "fast");
        CompletableFuture<String> slowFuture = asyncExecutor.supply(BIZ_QUERY_EXECUTOR, "slowQuery", () -> {
            sleep(300);
            return "slow";
        });

        String value = asyncTemplate.anyOf(List.of(fastFuture, slowFuture)).join();

        return Map.of(
                "api", "AsyncTemplate.anyOf",
                "meaning", "第一个完成的任务决定结果，不等全部任务",
                "value", value
        );
    }

    /**
     * withTimeout 示例：给一个 Future 增加结果层超时。
     *
     * @return 超时结果
     */
    @GetMapping("/with-timeout")
    public Map<String, Object> withTimeout() {
        CompletableFuture<String> slowFuture = asyncExecutor.supply(BIZ_QUERY_EXECUTOR, "slowTimeoutQuery", () -> {
            sleep(500);
            return "slow";
        });

        try {
            asyncTemplate.withTimeout(slowFuture, Duration.ofMillis(100)).join();
            return Map.of("api", "AsyncTemplate.withTimeout", "timeout", false);
        } catch (CompletionException ex) {
            return Map.of(
                    "api", "AsyncTemplate.withTimeout",
                    "meaning", "CompletableFuture 超时只代表结果层超时，不保证底层线程已经中断",
                    "timeout", true,
                    "error", rootMessage(ex)
            );
        }
    }

    /**
     * withFallback 示例：失败时返回降级值。
     *
     * @return fallback 结果
     */
    @GetMapping("/with-fallback")
    public Map<String, Object> withFallback() {
        CompletableFuture<String> failureFuture = asyncExecutor.supply(BIZ_QUERY_EXECUTOR, "failureQuery", () -> {
            throw new IllegalStateException("remote service error");
        });

        String value = asyncTemplate.withFallback(failureFuture, ex -> "fallback-value").join();

        return Map.of(
                "api", "AsyncTemplate.withFallback",
                "meaning", "任务失败后返回降级值",
                "value", value
        );
    }

    /**
     * 提取异常根因信息。
     *
     * @param ex CompletionException
     * @return 异常说明
     */
    private String rootMessage(CompletionException ex) {
        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
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
