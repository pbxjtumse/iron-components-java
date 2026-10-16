package com.xjtu.iron.concurrency.demo;

import com.xjtu.iron.concurrency.api.execution.executor.AsyncExecutor;
import com.xjtu.iron.concurrency.api.execution.registry.TaskExecutionRegistry;
import com.xjtu.iron.concurrency.api.execution.task.AsyncTask;
import com.xjtu.iron.concurrency.demo.error.DemoDomainException;
import com.xjtu.iron.concurrency.demo.error.DemoRpcException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;

/**
 * CompositeAsyncErrorClassifier 使用示例。
 */
@RestController
@RequestMapping("/demo/error-classifier")
public final class ErrorClassificationDemoController {

    /**
     * 异步任务提交入口。
     */
    private final AsyncExecutor asyncExecutor;

    /**
     * 任务最新状态注册表。
     */
    private final TaskExecutionRegistry taskExecutionRegistry;

    public ErrorClassificationDemoController(
            AsyncExecutor asyncExecutor,
            TaskExecutionRegistry taskExecutionRegistry
    ) {
        this.asyncExecutor = asyncExecutor;
        this.taskExecutionRegistry = taskExecutionRegistry;
    }

    /**
     * 触发领域异常规则。
     */
    @GetMapping("/domain")
    public Map<String, Object> domainError() {
        String taskId = "domain-" + UUID.randomUUID();

        try {
            asyncExecutor.submit(
                    AsyncTask.of("default", "domainError", () -> {
                                throw new DemoDomainException(
                                        "ACCOUNT_STATUS_INVALID",
                                        "PAYMENT_POSTING",
                                        "账户状态非法"
                                );
                            })
                            .taskId(taskId)
                            .bizKey("accountId=10001")
            ).join();
        } catch (CompletionException ignored) {
            // Demo 通过任务注册表观察结构化错误，不在 Controller 继续抛出。
        }

        return result(taskId);
    }

    /**
     * 触发 RPC 依赖异常规则。
     */
    @GetMapping("/rpc")
    public Map<String, Object> rpcError() {
        String taskId = "rpc-" + UUID.randomUUID();

        try {
            asyncExecutor.submit(
                    AsyncTask.of("default", "rpcError", () -> {
                                throw new DemoRpcException(
                                        "user-service",
                                        "远程用户服务调用失败"
                                );
                            })
                            .taskId(taskId)
                            .bizKey("userId=10001")
            ).join();
        } catch (CompletionException ignored) {
            // Demo 通过任务注册表观察结构化错误，不在 Controller 继续抛出。
        }

        return result(taskId);
    }

    /**
     * 返回当前任务的结构化状态。
     */
    private Map<String, Object> result(String taskId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("snapshot", taskExecutionRegistry.get(taskId).orElse(null));
        return result;
    }
}
