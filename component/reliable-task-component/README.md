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

### 分包导航

包结构按稳定能力划分，而不是把所有命令、状态和结果堆在模块根包：

```text
reliable-task-api
└── api
    ├── client                    # 业务门面，只保留 Client 接口
    ├── execution                 # Handler、执行上下文和执行结果
    ├── model                     # ReliableTask、ReliableTaskKey
    ├── operation
    │   ├── admin                 # 人工操作结果
    │   ├── run                   # 单次尝试执行结果
    │   └── submit                # 提交请求与结果
    ├── repository
    │   ├── claim                 # CAS 抢占命令与结果
    │   ├── create                # 幂等创建结果
    │   ├── lease                 # 续租命令
    │   ├── scan                  # 到期候选查询
    │   └── transition            # 执行与人工状态迁移命令
    ├── scan                      # 调度器可调用的扫描门面
    └── state                     # 持久化状态机

reliable-task-core
└── core
    ├── client                    # Client 默认实现
    ├── execution
    │   └── handler               # 统一执行引擎与 Handler 注册表
    ├── policy                    # Lease、次数和退避等运行策略
    ├── scan                      # 扫描实现
    └── exception

reliable-task-provider-jdbc
└── provider.jdbc
    ├── repository                # Repository 实现
    ├── mapping                   # ResultSet 映射
    └── sql                       # SQL 构造与表名校验

reliable-task-spring-boot-starter
└── starter
    ├── autoconfigure             # 自动装配和本地扫描触发器
    └── properties                # 类型安全配置属性
```

`operation` 表示业务方直接发起的公开操作；`repository` 子包表示 Core 与存储实现之间的持久化协议。
两者不能混放，否则提交语义、执行语义和数据库 CAS 细节会重新耦合到一起。

当前仍处于 V1 收口阶段，本次直接迁移包名，不保留旧包下的 Deprecated 转发类，避免发布前就形成两套访问入口。

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
