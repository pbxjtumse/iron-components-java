# Idempotency 与 Storage Routing 集成设计及使用指南

> 本文以当前 master 代码为准：`StorageRouteAwareIdempotencyExecutor`、`StorageRoutingIdempotencyJdbcRouteResolver`、Direct / ShardingSphere-JDBC / ShardingSphere-Proxy 三种 StorageRouteMode 已存在。

## 1. 最终结论

幂等请求只描述幂等业务语义，不重复携带 Storage Routing 的物理分片模型：

```text
IdempotencyRequest
├── key
├── requestHash
├── routeKey            # 业务诊断/恢复元数据，不直接参与默认物理分片
├── storeName
├── scanBucket
├── policyName
└── policy
```

物理路由只有两条入口：

1. **外层业务已经绑定 `StorageRouteScope`**：幂等组件直接复用当前 `StorageRoute`，不重新 hash。
2. **当前没有业务路由**：`DefaultIdempotencyRouteContextFactory` 默认使用幂等 `key` 构造 `CompositeShardKey`，由 `StorageRouteResolver` 解析一次并绑定作用域。

真正负责这一层装饰的是当前代码中的：

```text
StorageRouteAwareIdempotencyExecutor
```

一次 `execute()/recover()` 最多调用一次全局 `StorageRouteResolver`。

## 2. 模块边界

```text
idempotent-api / idempotent-core
  幂等 key、状态机、owner/version、Recovery、结果策略

idempotent-provider-jdbc
  JDBC 状态持久化与 IdempotencyJdbcRoute

idempotent-integration-storage-routing
  StorageRouteAwareIdempotencyExecutor
  DefaultIdempotencyRouteContextFactory
  StorageRoutingIdempotencyJdbcRouteResolver
  StorageRouteAwareIdempotencyTransactionCoordinator

idempotent-integration-transaction
  SpringTransactionJdbcExecutionManager
  TransactionTemplateIdempotencyTransactionCoordinator

idempotent-starter
  自动装配 route-aware Executor、JDBC route resolver、事务协调器
```

API/Core 不依赖 Storage Routing 类型；Integration 模块负责组合两个组件。

## 3. 三种 key / context

| 名称 | 职责 | 是否决定物理分片 |
|---|---|---|
| `IdempotencyRequest.key` | 同一次逻辑请求身份 | 没有外层路由时，作为默认分片输入 |
| `routeKey` | 业务路由/诊断元数据，Recovery 原样沿用 | 否 |
| `scanBucket` | 已确定物理 shard 内的 Recovery 扫描分桶 | 否 |
| `StorageRouteContext` | 当前同步执行链真实物理/逻辑路由事实 | 是 |

`storeName` 是逻辑存储域，不是 Provider 名、数据库名或表名。

## 4. 一次执行只解析一次路由

```text
IdempotencyRequest
        │
        ├─ StorageRouteContext 已有 route ───────────┐
        │                                           │
        └─ 当前无 route                             │
             ↓                                      │
   DefaultIdempotencyRouteContextFactory            │
             ↓                                      │
   StorageRouteResolver.resolve() 仅一次             │
             ↓                                      │
   StorageRouteContext.open(route) ◀────────────────┘
             ↓
   delegate.execute/recover
      tryAcquire
        ↓
      business callback
        ↓
      markSuccess / markFailed
             ↓
        close scope
```

路由创建、解析或绑定失败会映射为 `REPOSITORY_ERROR`，并标记在当前 acquire/recover stage。

## 5. 为什么复用 shardInfo，而不是复用业务表名

同一 `ShardRouteInfo` 可以映射到不同表族：

```text
business mapping     -> db_05.business_order_56
idempotency mapping  -> db_05.iron_idempotency_record_56
outbox mapping       -> db_05.iron_outbox_56
```

因此幂等 JDBC 路由只复用 shard 事实，再用幂等自己的 `RouteMappingStrategy` 得到幂等表名。

## 6. 同库事务

要让 Business SQL 与 `markSuccess` 真正共享本地事务，必须同时满足：

1. 同一个 Spring 本地事务。
2. 两条路径解析到同一个目标 `dataSourceKey`。
3. TransactionManager 和 JDBC execution manager 使用同一个目标 DataSource。
4. 幂等状态 SQL 使用当前路由对应的幂等物理表。

`SpringTransactionJdbcExecutionManager` 通过 Spring 的事务绑定连接参与该事务；组件不会把跨库操作伪装成单库原子事务。

## 7. Direct / ShardingSphere-JDBC / Proxy

当前 Storage Routing 已存在：

```text
DIRECT_DATASOURCE
SHARDINGSPHERE_JDBC
PROXY
```

幂等集成不应重新实现三套状态机。差异只应该体现在 StorageRoute / SqlRoute 如何解析：

- Direct：Iron 负责解析 dataSourceKey + 物理表。
- ShardingSphere-JDBC：Iron 交付逻辑 DataSource / 逻辑表，ShardingSphere-JDBC 在进程内继续路由。
- Proxy：Iron 交付指向 ShardingSphere-Proxy 的普通 JDBC DataSource + 逻辑表，Proxy 在服务端继续路由。

无论哪种模式，`IdempotencyRequest` 都不应该重新增加物理 shard 字段。

## 8. Recovery

`scanBucket` 只能缩小扫描任务，不能推导物理 shard。真实分片模式下，外部 Reliable Task 应先枚举/绑定物理路由，再执行候选扫描和 `recover()`。

```text
physical shard enumeration
    ↓
StorageRouteContext.open(shardRoute)
    ↓
scanBucket query
    ↓
candidate
    ↓
recover(expectedOwner, expectedVersion)
```

候选快照不是执行许可，真正执行前仍必须经过 Repository 的第二次原子检查。

## 9. 验证清单

1. 无业务 route 时，默认按幂等 key 计算一次 route。
2. 有业务 route 时复用现有 `StorageRoute`，不重新 hash。
3. Business 表名不会被幂等 SQL 直接复用。
4. 一次 execute/recover 的状态操作共享同一个 route scope。
5. Recovery 未绑定必要物理 shard 时 fail-fast。
6. owner/version CAS 仍是幂等正确性核心，路由不替代并发控制。
7. 同库事务时 Business SQL 与状态 SQL 使用同一 transaction-bound Connection。
8. Direct / ShardingSphere-JDBC / Proxy 模式不改变 Idempotent Core 的状态机语义。
