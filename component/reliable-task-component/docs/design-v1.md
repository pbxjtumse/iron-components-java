# Reliable Task v1 设计

## 1. 目标

第一版解决以下问题：

1. 业务事务可以先持久化一条执行意图；提交后既可走快速路径，也可由扫描兜底。
2. 多实例并发扫描同一条任务时，只有一个 owner 能通过 CAS 获得执行权。
3. owner 宕机后，Lease 到期的 `RUNNING` 任务可以被其他实例重新抢占。
4. 旧 owner 即使稍后恢复，也不能覆盖新 owner 已经写入的状态。
5. 任务结果可以明确区分成功、可重试、等待对账、人工处理和最终失败。
6. 调度器是外部触发源，而不是任务正确性的组成部分；v1 可以不部署 XXL-JOB。

## 2. 非目标

v1 明确不实现：

- 不保证外部 RPC、支付或消息发送 exactly-once。
- 不自动序列化并反射回放任意 Java 方法。
- 不实现 DAG、工作流、Saga 编排和 MapReduce。
- 不把每一条业务任务注册成调度平台 Job。
- 不在 Core 中直接依赖 `retry-component`；具体 Handler 可以自行组合本地短重试。
- 不在 v1 枚举所有物理数据库分片；分片拓扑扫描由后续 Storage Routing integration 提供。

## 3. 分层

```text
业务 / Outbox / Remote Command / Idempotency Recovery
                         |
                         v
                  reliable-task-api
                         |
                         v
                  reliable-task-core
                         |
                         v
              ReliableTaskRepository
                         |
                         v
       reliable-task-provider-jdbc -> relational-access

触发源：runNow / local scheduler / manual / future XXL-JOB
```

`Outbox`、`Remote Command` 和 `Idempotency Recovery` 是任务语义适配层，不进入通用 Core。

### 3.1 包职责

| 模块 | 包 | 职责 |
|---|---|---|
| API | `api.client` | 业务提交、查询、立即尝试执行和人工操作门面 |
| API | `api.operation.submit/run/admin` | 各公开操作自己的输入、状态和结果 |
| API | `api.execution` | Handler SPI、执行上下文、Lease 续租入口和执行结果 |
| API | `api.model`、`api.state` | 任务快照、任务标识和持久化状态机 |
| API | `api.repository.*` | Core 与 Provider 之间的 create/claim/lease/scan/transition 协议 |
| API | `api.scan` | 本地或外部调度器使用的扫描入口 |
| Core | `core.execution` | 唯一的 `claim -> handler -> transition` 执行模板 |
| Core | `core.execution.handler` | Handler 注册和按 `taskType` 查找 |
| Core | `core.client`、`core.scan` | 业务快速路径、人工操作和扫描路径的默认实现 |
| Core | `core.policy` | 最大次数、扫描桶、Lease 与失败退避策略 |
| JDBC | `provider.jdbc.repository` | Repository 端口实现和 SQL 参数编排 |
| JDBC | `provider.jdbc.mapping` | `ResultSet` 到任务快照的映射 |
| JDBC | `provider.jdbc.sql` | 固定表 SQL 与动态表名白名单校验 |
| Starter | `starter.autoconfigure` | Spring Bean 与本地调度触发器装配 |
| Starter | `starter.properties` | `xjtu.iron.reliable-task` 配置模型 |

分包只表达稳定职责，不为每个类机械创建一层目录。例如 Handler、Context、Outcome 和 Result 共同构成一次
执行契约，因此保留在同一个 `api.execution` 包；JDBC 的 SQL 和行映射已经是独立变化点，因此从 Repository
中拆出。

### 3.2 数据对象约定

- 任务快照、操作结果和 Repository 命令使用普通不可变 `final class`，不使用 Java `record`。
- 属性保持 `private final`，通过构造方法完成校验，并提供传统 `getXxx()` / `isXxx()` 访问器。
- 值对象保留 `equals`、`hashCode` 和 `toString`，避免从 `record` 改为普通类后丢失值语义。
- 每个类属性必须提供中文 Javadoc，明确空值、默认值、并发控制或路由语义。

## 4. 持久状态

| 状态 | 含义 | 扫描是否可见 |
|---|---|---:|
| `READY` | 首次执行尚未开始 | 到期后可见 |
| `RUNNING` | 某个 owner 持有 Lease | Lease 过期后可见 |
| `RETRY_WAIT` | 明确失败，等待再次执行 | 到期后可见 |
| `WAIT_RECONCILE` | 外部结果未知，下一次应查询真实结果 | 到期后可见 |
| `MANUAL` | 自动执行停止，等待人工决定 | 否 |
| `SUCCEEDED` | 成功终态 | 否 |
| `DEAD` | 不可恢复或达到最大执行次数 | 否 |
| `CANCELLED` | 被业务或运维取消 | 否 |

允许的主状态迁移：

```text
READY / RETRY_WAIT / WAIT_RECONCILE / expired RUNNING
                         |
                         | tryClaim
                         v
                      RUNNING
       +-----------------+------------------+
       |                 |                  |
       v                 v                  v
  SUCCEEDED         RETRY_WAIT       WAIT_RECONCILE
                                            |
                           +----------------+----------------+
                           v                v                v
                        MANUAL            DEAD          CANCELLED
```

## 5. 并发正确性

### 5.1 Candidate 不是执行许可

扫描 SQL 返回的记录只是快照。真正执行之前必须执行条件更新：

```sql
UPDATE iron_reliable_task
SET status = 'RUNNING', owner_id = ?, lease_until = ?,
    attempt_count = attempt_count + 1, version = version + 1
WHERE store_name = ? AND task_id = ? AND version = ?
  AND (
      (status IN ('READY','RETRY_WAIT','WAIT_RECONCILE') AND next_execute_at <= ?)
      OR (status = 'RUNNING' AND lease_until <= ?)
  );
```

影响行数为 `1` 才获得执行权。

### 5.2 owner + version 防止旧执行者提交

Handler 完成后的状态写入必须满足：

```sql
WHERE status = 'RUNNING' AND owner_id = ? AND version = ?
```

新 owner 抢占过期任务时会推进 `version`。旧 owner 的完成更新因此为零行，不会覆盖新状态。

这只能保护本地任务状态，不能撤销旧 owner 已经产生的事务外副作用。外部系统仍需要业务幂等号、
fencing token 或查询对账。

### 5.3 Lease 与续租

Lease 不是执行超时中断器。它只描述 owner 的执行权有效期。运行时间可能超过 Lease 的 Handler 必须调用
`context.renewLease(...)`；续租同样校验 owner/version。

## 6. 次数语义

`attempt_count` 在每次成功抢占时增加一次，代表 Reliable Task 的持久化执行轮次，不代表
`retry-component` 内部的物理尝试次数。

```text
总下游调用上限
≈ Reliable Task 执行轮次 × Retry Component 本地尝试次数 × SDK 自带重试次数
```

当 Handler 返回 `RETRY` 或发生未处理异常，且本次 `attempt_count >= max_attempts` 时，Core 转为 `DEAD`。
`RECONCILE` 同样受该上限保护，避免永久无界扫描；需要长期等待的业务应配置更高上限或进入人工流程。

人工重新入队通过 `ReliableTaskAdminClient.requeue(key, expectedVersion, executeAt)` 完成。它只接受
`MANUAL/DEAD`，重置 `attempt_count` 并推进 `version`；取消操作不允许直接中断 `RUNNING` Handler。

## 7. 快速路径与扫描兜底

```text
Tx1：业务数据 + Reliable Task INSERT
                         |
                       COMMIT
                         |
               client.runNow(taskId)
                         |
                      CAS claim
                         |
          成功则当前线程立即执行

若进程在 COMMIT 后、runNow 前宕机：扫描器稍后发现 READY 任务并执行。
```

快速路径与扫描路径不会分别实现业务调用；二者最终都进入同一个 `ReliableTaskEngine`。

## 8. 事务边界

JDBC Provider 基于 `RelationalTemplate`。在 Spring 环境中，`relational-starter` 使用事务感知 Connection Provider：

- 外层存在绑定同一 DataSource 的本地事务时，任务 INSERT 参与该事务。
- 外层没有事务时，INSERT 按普通数据库调用提交。
- Core 不主动开启业务事务；业务应用负责用 transaction-component 或 Spring Transaction 包裹
  “业务数据 + 任务意图”。

任务执行结果更新是独立的短 SQL CAS。业务 Handler 若需要“业务 SQL + SUCCEEDED”严格原子提交，后续 integration
应提供显式事务协调器；v1 不做虚假的原子性承诺。

## 9. 分库分表预留

任务记录保存：

- `store_name`：逻辑任务 Store；
- `route_key`：重新解析业务/任务物理路由的稳定键；
- `scan_bucket`：物理 shard 内部的稳定扫描桶。

`scan_bucket` 不等于 ShardingSphere shardId，也不等于 XXL-JOB shardIndex。

v1 JDBC Provider 使用一个固定表。后续 Storage Routing integration 要增加：

1. 点访问：`route_key -> StorageRoute -> SqlRoute + physical table`；
2. 离线扫描：枚举 physical scan target，再在每个 target 内扫描 `scan_bucket`；
3. 执行前重新绑定 `StorageRouteScope`，不能依赖提交线程的 ThreadLocal。

## 10. 本地调度器

Starter 提供默认关闭的 Spring `@Scheduled` 适配器。每个实例可以扫描全部 bucket，重复扫描通过 CAS 消解。
它适合 Demo、开发和小规模部署，不提供集中控制台、分片分配和调度告警。

未来接入 XXL-JOB 时：

```text
XXL shardIndex/shardTotal -> 分配 scanBucket -> ReliableTaskScanner.scan(...)
```

不修改 Repository、Engine、Handler 或状态机。

## 11. v1 验收点

- 同一个 `taskId` 重复提交不会生成两条记录。
- 两个 owner 同时 claim，只有一个成功。
- Lease 到期后允许新 owner 接管。
- 旧 owner 完成写入被拒绝。
- Handler 成功进入 `SUCCEEDED`。
- 异常按延迟进入 `RETRY_WAIT`，达到上限进入 `DEAD`。
- 结果未知可以进入 `WAIT_RECONCILE`。
- 缺失 Handler 进入 `MANUAL`，不进行无意义风暴重试。
- 本地扫描器默认关闭。
