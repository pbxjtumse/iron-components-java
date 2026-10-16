package com.xjtu.iron.concurrency.demo;

import com.xjtu.iron.concurrency.api.execution.executor.AsyncExecutor;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 上下文传播示例。
 *
 * <p>用于验证请求线程中的 MDC 是否能传播到线程池工作线程。</p>
 */
@RestController
@RequestMapping("/demo/context")
public class ContextDemoController {

    /**
     * 默认线程池名称。
     */
    private static final String DEFAULT_EXECUTOR = "default";

    /**
     * 异步执行器。
     */
    private final AsyncExecutor asyncExecutor;

    /**
     * 创建上下文传播示例 Controller。
     *
     * @param asyncExecutor 异步执行器
     */
    public ContextDemoController(AsyncExecutor asyncExecutor) {
        this.asyncExecutor = asyncExecutor;
    }

    /**
     * MDC 示例：主线程设置 traceId，异步线程读取 traceId。
     *
     * @return 主线程和异步线程中的 traceId
     */
    @GetMapping("/mdc")
    public Map<String, Object> mdc() {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);

        try {
            CompletableFuture<String> future = asyncExecutor.supply(
                    DEFAULT_EXECUTOR,
                    "readMdcTraceId",
                    () -> MDC.get("traceId")
            );

            return Map.of(
                    "api", "ContextAwareTaskDecorator + MdcContextPropagator",
                    "mainThreadTraceId", traceId,
                    "asyncThreadTraceId", future.join()
            );
        } finally {
            MDC.remove("traceId");
        }
    }
}
