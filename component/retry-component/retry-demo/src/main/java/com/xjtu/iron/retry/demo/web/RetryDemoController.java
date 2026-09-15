package com.xjtu.iron.retry.demo.web;

import com.xjtu.iron.retry.api.backoff.BackoffStrategies;
import com.xjtu.iron.retry.api.backoff.RetryDelaySource;
import com.xjtu.iron.retry.api.execution.RetryCancellationToken;
import com.xjtu.iron.retry.api.execution.RetryExecution;
import com.xjtu.iron.retry.api.execution.RetryExecutor;
import com.xjtu.iron.retry.api.execution.RetryResult;
import com.xjtu.iron.retry.api.policy.OperationSafety;
import com.xjtu.iron.retry.api.policy.RetryDecision;
import com.xjtu.iron.retry.api.policy.RetryFailureCategory;
import com.xjtu.iron.retry.api.policy.RetryPolicy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** 展示异常、结果、服务端退避、取消和不可重试场景。 */
@RestController
@RequestMapping("/demo/retry")
public class RetryDemoController {

    /** 统一重试执行入口。 */
    private final RetryExecutor retryExecutor;

    public RetryDemoController(RetryExecutor retryExecutor) {
        this.retryExecutor = retryExecutor;
    }

    /** 展示 IOException 最终成功的异常重试。 */
    @GetMapping("/exception")
    public Map<String, Object> retryException(
            @RequestParam(defaultValue = "2") int failures) {
        AtomicInteger executions = new AtomicInteger();
        RetryResult<String> result = retryExecutor.execute(
                "demo-exception",
                Map.of("downstream", "demo-service"),
                context -> {
                    int current = executions.incrementAndGet();
                    if (current <= failures) {
                        throw new IOException(
                                "Simulated transient failure, execution=" + current
                        );
                    }
                    return "SUCCESS";
                },
                "remote-call"
        );
        return response(result, executions.get());
    }

    /** 展示返回 PENDING 时触发结果重试。 */
    @GetMapping("/result")
    public Map<String, Object> retryResult(
            @RequestParam(defaultValue = "2") int pendingTimes) {
        AtomicInteger executions = new AtomicInteger();
        RetryPolicy policy = RetryPolicy.builder("demo-result")
                .maxAttempts(4)
                .maxDuration(Duration.ofSeconds(3))
                .operationSafety(OperationSafety.READ_ONLY)
                .retryIfResult(
                        "PENDING"::equals,
                        RetryFailureCategory.RESULT_NOT_READY,
                        "PAYMENT_PROCESSING"
                )
                .backoffStrategy(BackoffStrategies.fixed(Duration.ofMillis(100)))
                .build();
        RetryResult<String> result = retryExecutor.execute(
                "demo-result",
                context -> executions.incrementAndGet() <= pendingTimes
                        ? "PENDING"
                        : "SUCCESS",
                policy
        );
        return response(result, executions.get());
    }

    /** 展示分类器使用服务端建议等待时间覆盖默认退避。 */
    @GetMapping("/server-delay")
    public Map<String, Object> serverDirectedDelay() {
        AtomicInteger executions = new AtomicInteger();
        RetryPolicy policy = RetryPolicy.builder("server-directed")
                .maxAttempts(3)
                .maxDuration(Duration.ofSeconds(3))
                .operationSafety(OperationSafety.READ_ONLY)
                .classifier(attempt -> {
                    if (attempt.getAttemptNumber() == 1) {
                        return RetryDecision.retryAfter(
                                Duration.ofMillis(150),
                                RetryDelaySource.SERVER_DIRECTED,
                                "downstream returned Retry-After",
                                "HTTP_429",
                                RetryFailureCategory.THROTTLING
                        );
                    }
                    return RetryDecision.success("downstream accepted request");
                })
                .backoffStrategy(BackoffStrategies.exponentialWithFullJitter(
                        Duration.ofMillis(50),
                        Duration.ofMillis(500),
                        2.0D
                ))
                .build();
        RetryResult<String> result = retryExecutor.execute(
                "demo-server-delay",
                context -> "EXECUTION_" + executions.incrementAndGet(),
                policy
        );
        return response(result, executions.get());
    }

    /** 展示业务操作在第一次失败后请求协作式取消。 */
    @GetMapping("/cancel")
    public Map<String, Object> cancelBeforeNextAttempt() {
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicInteger executions = new AtomicInteger();
        RetryPolicy policy = RetryPolicy.builder("cancel-demo")
                .maxAttempts(3)
                .maxDuration(Duration.ofSeconds(2))
                .retryOn(IOException.class)
                .backoffStrategy(BackoffStrategies.fixed(Duration.ofMillis(10)))
                .build();
        RetryExecution<String> execution = RetryExecution
                .<String>builder(
                        "demo-cancel",
                        context -> {
                            executions.incrementAndGet();
                            cancelled.set(true);
                            throw new IOException("cancel before second attempt");
                        },
                        policy
                )
                .cancellationToken(RetryCancellationToken.from(cancelled))
                .retryId("demo-cancel-request")
                .build();
        RetryResult<String> result = retryExecutor.execute(execution);
        return response(result, executions.get());
    }

    /** 展示未匹配 retry-on 的永久异常只执行一次。 */
    @GetMapping("/non-retryable")
    public Map<String, Object> nonRetryable() {
        AtomicInteger executions = new AtomicInteger();
        RetryResult<String> result = retryExecutor.execute(
                "demo-non-retryable",
                context -> {
                    executions.incrementAndGet();
                    throw new IllegalArgumentException("Simulated permanent failure");
                },
                "remote-call"
        );
        return response(result, executions.get());
    }

    /** 将统一结果转换为便于浏览器观察的响应映射。 */
    private Map<String, Object> response(
            RetryResult<?> result,
            int physicalExecutions) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("retryId", result.getRetryId());
        response.put("operationName", result.getOperationName());
        response.put("policyName", result.getPolicyName());
        response.put("status", result.getStatus());
        response.put("attempts", result.getAttempts());
        response.put("physicalExecutions", physicalExecutions);
        response.put("elapsedMillis", result.getElapsedTime().toMillis());
        response.put("failureCategory", result.getFailureCategory());
        response.put("failureCode", result.getFailureCode());
        response.put(
                "decisionReason",
                result.getLastDecision() == null
                        ? null
                        : result.getLastDecision().getReason()
        );
        response.put("value", result.getValue());
        response.put(
                "failureType",
                result.getFailure() == null
                        ? null
                        : result.getFailure().getClass().getName()
        );
        response.put(
                "failureMessage",
                result.getFailure() == null
                        ? null
                        : result.getFailure().getMessage()
        );
        return response;
    }
}
