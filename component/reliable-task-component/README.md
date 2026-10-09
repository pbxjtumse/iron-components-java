# Reliable Task Component

`reliable-task-component` 是持久化任务执行内核。它保证任务在应用进程重启、节点切换和并发扫描后仍可继续推进，
但不会假装提供 exactly-once 外部副作用。

第一版不依赖 XXL-JOB、SnailJob 或其他调度平台。所有执行入口统一经过数据库 CAS 抢占：

```text
submit / runNow / local scan / future XXL-JOB
                    |
                    v
             tryClaim(CAS + Lease)
                    |
                    v
                 handler
                    |
                    v
     success / retry / reconcile / manual / dead
```

## 模块

- `reliable-task-api`：稳定模型、Handler、Client、Scanner 和 Repository 协议。
- `reliable-task-core`：状态机、抢占执行、快速路径、扫描和 Handler 注册表。
- `reliable-task-provider-jdbc`：基于 Relational Access 的 JDBC 持久化实现。
- `reliable-task-spring-boot-starter`：自动装配和默认关闭的本地扫描器。

完整设计见 [docs/design-v1.md](docs/design-v1.md)，建表脚本见
[reliable-task-provider-jdbc/src/main/resources/schema-mysql.sql](reliable-task-provider-jdbc/src/main/resources/schema-mysql.sql)。

## 最小用法

```java
@Component
final class DemoTaskHandler implements ReliableTaskHandler {
    @Override
    public String taskType() {
        return "demo";
    }

    @Override
    public ReliableTaskExecutionResult execute(ReliableTaskExecutionContext context) {
        // 业务可以在这里使用 retry-component 做本轮毫秒级短重试。
        return ReliableTaskExecutionResult.success();
    }
}
```

```java
ReliableTaskSubmitResult result = client.submit(
        ReliableTaskSubmission.builder("demo", "payload-json")
                .taskId("business-command-10001")
                .routeKey("order-10001")
                .build()
);

// 快速路径仍然先 CAS 抢占，不直接绕过持久化任务。
client.runNow(result.task().getKey());
```

本地扫描器默认关闭：

```yaml
xjtu:
  iron:
    reliable-task:
      local-scheduler:
        enabled: true
        fixed-delay: 5s
```

生产环境后续接入 XXL-JOB 时，只需要由 JobHandler 调用 `ReliableTaskScanner.scan(...)`。

`ReliableTaskAdminClient` 提供最小人工入口：`MANUAL/DEAD` 可以携带观察到的 `version` 重新入队，
尚未运行的自动任务可以取消。它只提供安全内核，不在 v1 建设管理后台。
