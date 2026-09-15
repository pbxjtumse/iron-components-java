package com.xjtu.iron.concurrency.starter.endpoint;

import com.xjtu.iron.concurrency.api.execution.pool.ThreadPoolManager;
import com.xjtu.iron.concurrency.api.execution.pool.ThreadPoolSnapshot;
import com.xjtu.iron.concurrency.api.execution.registry.TaskExecutionRegistry;
import com.xjtu.iron.concurrency.api.execution.registry.TaskExecutionSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 并发组件只读管理接口。
 */
@RestController
@RequestMapping("${xjtu.iron.concurrency.management.base-path:/iron-concurrency}")
@ConditionalOnClass(RestController.class)
@ConditionalOnProperty(prefix = "xjtu.iron.concurrency.management", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IronConcurrencyReadOnlyController {

    private final ThreadPoolManager threadPoolManager;
    private final TaskExecutionRegistry taskExecutionRegistry;

    public IronConcurrencyReadOnlyController(
            ThreadPoolManager threadPoolManager,
            TaskExecutionRegistry taskExecutionRegistry
    ) {
        this.threadPoolManager = threadPoolManager;
        this.taskExecutionRegistry = taskExecutionRegistry;
    }

    @GetMapping("/thread-pools")
    public Map<String, ThreadPoolSnapshot> threadPools() {
        return threadPoolManager.snapshots();
    }

    @GetMapping("/thread-pools/{executorName}")
    public ThreadPoolSnapshot threadPool(@PathVariable String executorName) {
        return threadPoolManager.snapshot(executorName);
    }

    @GetMapping("/tasks")
    public List<TaskExecutionSnapshot> tasks(@RequestParam(defaultValue = "100") int limit) {
        return taskExecutionRegistry.recent(limit);
    }

    @GetMapping("/tasks/{taskId}")
    public TaskExecutionSnapshot task(@PathVariable String taskId) {
        return taskExecutionRegistry.get(taskId).orElse(null);
    }
}
