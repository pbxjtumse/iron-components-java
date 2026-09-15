# IdempotencyStorage 与 Storage Routing 集成设计

> 本文描述 `idempotent-component v2 — Shard-Ready Storage` 当前实现，只保留推荐模型，不维护旧路由兼容路线。

## 1. 设计目标

Idempotency 的正确性模型和物理存储路由必须解耦：

```text
Idempotency Core
  ownerToken + version
  PROCESSING / SUCCESS / FAILED / DISCARDED
  Acquire / Recovery / Completion CAS
        │
        ▼
IdempotencyRepository
        │
        ▼
Idempotency JDBC Routing
        │
        ├─ 复用当前业务 shardInfo（优先）
        └─ Idempotency shardKey fallback（无业务路由时）
        │
        ▼
Idempotency-specific RouteMappingStrategy
        │
        ▼
dataSourceKey + physical idempotency table
        │
        ▼
JdbcIdempotencyRepository
```

关键原则：

1. **同一个业务分片共享的是 `CompositeShardKey / ShardRouteInfo`，不是物理表名。**
2. 业务表、幂等表、Outbox 表可以在同一数据库 shard，但必须分别映射自己的物理表。
3. Idempotency Core 不依赖 Storage Routing 的类型，只有 integration 模块依赖二者。
4. `scanBucket` 不是 shardId/tableIndex；Recovery 是“物理 shard 枚举 × scanBucket”的二维扫描。
5. Storage Routing 只回答“去哪”；owner/version、状态 CAS、事务语义仍属于 Idempotency。

## 2. 三层路由身份

### 2.1 IdempotencyStorageContext

```java
IdempotencyStorageContext {
    String storeName;
    long shardKey;
    int scanBucket;
}
```

- `storeName`：逻辑 Store/场景，例如 `payment`、`message-consume`。
- `shardKey`：无业务路由上下文时的点路由 fallback key。
- `scanBucket`：Recovery 在单个物理 shard 内使用的逻辑扫描桶。

它故意不知道 `db_03`、`iron_idempotency_record_017`、`StorageRoute` 或 `SqlRoute`。

### 2.2 StorageRouteContext

业务入口已经完成路由时，会把完整 `StorageRoute` 绑定到当前执行作用域：

```text
RouteContext
  CompositeShardKey(tenant_id, order_id, ...)
        ↓
ShardResolver
        ↓
ShardRouteInfo
        ↓
Business MappingStrategy
        ↓
Business PhysicalStorageLocation
        ↓
StorageRouteContext.open(route)
```

后续 Business Repository、Idempotency、Outbox、Task Storage 都可以读取同一份分片身份。

### 2.3 IdempotencyJdbcRoute

JDBC Provider 最终只需要：

```java
IdempotencyJdbcRoute {
    String dataSourceKey;
    String tableName;
}
```

它是最终执行位置，不携带幂等状态，也不负责分片计算。

## 3. 为什么不能直接复用业务 StorageRoute.location

Storage Routing 当前模型把计算与映射分开：

```text
CompositeShardKey
    ↓
ShardResolver
    ↓
ShardRouteInfo
    ↓
RouteMappingStrategy
    ↓
PhysicalStorageLocation
```

同一个 `ShardRouteInfo(56, db=5, localTable=6)` 可以有不同表族：

```text
business mapping     -> db_05.business_order_56
idempotency mapping  -> db_05.iron_idempotency_record_56
outbox mapping       -> db_05.iron_outbox_56
```

因此 Idempotency Integration **只能复用 shardInfo，不能复用 business physical table**。

这也是本轮打通的核心：

```text
错误：
current business StorageRoute.location
    -> business_order_56
    -> Idempotency SQL 直接执行            X

正确：
current business StorageRoute.shardInfo
    -> Idempotency RouteMappingStrategy
    -> iron_idempotency_record_56          ✓
```

对于没有 `shardInfo` 的 DIRECT_DATASOURCE 也遵守同一原则：只复用 `dataSourceKey`，不复用业务 `tableName`。

## 4. Point Access 主流程

### 4.0 一次 execute 只决定一次 StorageRoute

Starter 在核心 `IdempotencyExecutor` 外层装配
`StorageRouteAwareIdempotencyExecutor`：

```text
IdempotencyRequest
    ↓
IdempotencyRouteContextFactory
    ↓  默认使用 idempotency key
StorageRouteResolver.resolve(...)         仅一次
    ↓
StorageRouteContext.open(route)
    ├─ tryAcquire
    ├─ Business callback
    └─ markSuccess / markFailed
    ↓
close scope
```

这样 Repository 的每次状态操作虽然仍会解析自己的幂等物理表，但读取的是同一个已绑定
`StorageRoute`，不会在 `tryAcquire`、完成状态和失败状态之间重复 hash。

默认的 `DefaultIdempotencyRouteContextFactory` 使用幂等 key 作为单字段分片键。业务可以替换
`IdempotencyRouteContextFactory`，改为 tenantId、merchantId 或复合键；一旦业务入口已经绑定
`StorageRoute`，装饰器将直接复用业务路由，Factory 和全局 Resolver 都不会再次调用。

### 4.1 当前已有业务 StorageRoute

这是业务写 + 幂等状态需要同分片、同本地事务时的推荐路径。

```text
Business entry
    ↓
StorageRouteResolver.resolve(business RouteContext)
    ↓
StorageRouteContext.open(businessRoute)
    ↓
Business Repository
    └─ uses business table

Idempotency Executor
    ↓
RoutedJdbcIdempotencyRepository
    ↓
StorageRoutingIdempotencyJdbcRouteResolver.resolvePoint(...)
    ↓
read current StorageRoute
    ↓
reuse CompositeShardKey + ShardRouteInfo
    ↓
Idempotency RouteMappingStrategy.map(shardInfo)
    ↓
IdempotencyJdbcRoute(db_XX, iron_idempotency_record_YY)
    ↓
JdbcIdempotencyRepository
```

注意：这里不会再次 hash。业务入口已经决定了 shard，技术组件必须复用同一个 shard 事实。

如果当前绑定的是固定 DIRECT_DATASOURCE 且没有 shardInfo，则：

```text
reuse business dataSourceKey
    +
use idempotency directTableName
```

不会把业务表名带入幂等 SQL。

### 4.2 没有业务 StorageRoute

例如独立消息消费、后台任务或单独调用 Idempotency 时：

```text
IdempotencyStorageContext.shardKey
    ↓
CompositeShardKey(idempotency_shard_key, shardKey)
    ↓
StorageRouteResolver
    ↓
ShardRouteInfo
    ↓
丢弃 resolver 的 business physical table（若有）
    ↓
Idempotency RouteMappingStrategy.map(shardInfo)
    ↓
IdempotencyJdbcRoute
```

这里 `shardKey` 才真正承担 fallback 路由职责。

## 5. Recovery 路由模型

Recovery 没有单条业务请求天然携带的 shardKey，因此不能用一个 `scanBucket` 推导物理库表。

正确模型是：

```text
Physical Shard Enumeration
    ×
scanBucket Enumeration
```

例如 100 个物理 shard、1024 个逻辑 bucket：

```text
shard 0
  bucket 0
  bucket 1
  ...

shard 1
  bucket 0
  bucket 1
  ...
```

外部 Reliable Task 的职责：

```text
for each physical shard:
    open StorageRouteContext(shard route)
    for assigned scanBucket:
        IdempotencyRecoveryQueryService.findCandidates(...)
```

Idempotency 的职责：

```text
bound shardInfo
    ↓
map to idempotency physical table
    ↓
WHERE store_name = ?
  AND scan_bucket = ?
  AND recovery_mode = 'EXTERNAL_TASK'
  AND status = 'PROCESSING' / retryable FAILED
  ...
```

如果当前使用真实分片 resolver，但 Recovery 没有绑定任何物理 shard，上层直接扫描会 fail-fast，而不是猜测库表。

## 6. storeName / shardKey / scanBucket 的最终关系

```text
storeName
= 幂等逻辑 Store / 场景隔离
= 参与幂等记录唯一身份
= 不等于 Provider / DataSource / table

shardKey
= 无业务 StorageRoute 时的稳定 point-routing fallback
= 有 bound shardInfo 时不参与重新分片

scanBucket
= Recovery 扫描并行度维度
= 只在已经确定的物理 shard 内过滤
= 不等于 shardId / databaseIndex / tableIndex
```

不要建立以下错误等式：

```text
storeName == databaseName      X
shardKey == tableIndex         X
scanBucket == tableIndex       X
```

## 7. RoutedJdbcIdempotencyRepository 的职责

`RoutedJdbcIdempotencyRepository` 是 JDBC Provider 的路由门面：

```text
IdempotencyRepository API
      ↓
RoutedJdbcIdempotencyRepository
      ↓
IdempotencyJdbcRouteResolver
      ↓
IdempotencyJdbcRoute
      ↓
JdbcExecutionManagerResolver
      ↓
JdbcIdempotencyRepository(dataSourceKey + physicalTable)
```

它不复制 SQL 状态机；真正的：

- UNIQUE
- SELECT FOR UPDATE
- ownerToken/version CAS
- WINDOWED rollover
- Recovery CAS
- SUCCESS / FAILED / DISCARDED

仍然只在 `JdbcIdempotencyRepository` 中实现。

## 8. 与 Relational Access 的边界

本轮完成的是 **Storage Routing 路由正确性闭环**。

当前 JDBC Provider 仍使用 `JdbcExecutionManager + raw JDBC`。下一阶段可以按已有 Relational Access 设计迁移：

```text
JdbcIdempotencyRepository / future JdbcIdempotencyStorage
      ↓
RelationalTemplate
      ↓
ConnectionProvider / DataSourceResolver
```

同时把事务职责进一步收口：

```text
Tx-A REQUIRES_NEW
    TransactionExecutor
      -> Idempotency JDBC Storage
      -> RelationalTemplate

Tx-B REQUIRED
    Business
    + markSuccess(owner/version)
    use same transaction-bound Connection

Tx-C REQUIRES_NEW
    markFailed
```

Relational Access 只负责执行已确定 SQL；它不理解 ACQUIRED、PROCESSING、ownerToken/version，也不决定事务传播级别。

### 8.1 同连接事务的成立条件

`SpringTransactionJdbcExecutionManager` 通过 `DataSourceUtils` 获取当前事务绑定的连接，因此在同一个
Spring 本地事务中，Business SQL 与 `markSuccess` 可以复用同一条 `Connection`。但在多数据源拓扑中还需要
满足一个额外条件：事务执行器/事务管理器必须与当前 `StorageRoute.dataSourceKey` 指向同一个 DataSource。

```text
StorageRoute.dataSourceKey = db_03
        ↓
JdbcExecutionManager(db_03)
        +
TransactionManager(db_03)
        ↓
same transaction-bound Connection
```

固定使用 `db_00` 的事务管理器、却把 Repository 路由到 `db_03`，不会形成同库本地事务。生产级 10 库模式
需要按 `dataSourceKey` 选择匹配的 `JdbcExecutionManager + TransactionExecutor` 组合；本阶段先固定并验证这一约束，
不把它伪装成跨库原子事务。

## 9. 配置关系

Storage Routing 提供共享拓扑：

```text
xjtu.iron.storage-routing.resolver
  data-source-prefix
  database-count
  tables-per-database
  data-source-index-width
  table-index-width
  table-index-mode
```

Idempotency 路由配置独立表达两个概念：

```text
xjtu.iron.idempotent.jdbc.routing.enabled=true
xjtu.iron.idempotent.jdbc.routing.logical-table=iron_idempotency_record
xjtu.iron.idempotent.jdbc.routing.table-prefix=iron_idempotency_record
```

- `logical-table` 只进入 `RouteContext`，表达逻辑表族。
- `table-prefix` 只用于把共享 `ShardRouteInfo` 映射为幂等物理表。

例如：

```text
iron_idempotency_record_00
iron_idempotency_record_01
...
```

固定单库单表模式完全独立，继续使用：

```text
xjtu.iron.idempotent.jdbc.table-name=iron_idempotency_record
```

这样 `table-name` 不再同时承担“固定物理表名”和“分片表前缀”两种语义。

## 10. 当前不做的事情

- Idempotency Core 不管理 Storage Routing 拓扑。
- Idempotency 不自己实现 hash 分片算法。
- `scanBucket` 不承担物理路由。
- Integration 不复用业务表 physical location。
- Recovery 不在 Idempotency 内部启动定时扫描线程。
- 本轮不同时重写 JDBC SQL 到 RelationalTemplate，避免把“路由正确性”和“SQL/事务执行迁移”混成一个不可验证的大改动。
- 本轮不提供跨库分布式事务；同连接保证只适用于业务 SQL 与幂等 SQL 落在同一 dataSource 的本地事务。

## 11. 验证重点

Integration 测试至少固定以下语义：

1. 无业务路由时，fallback shardKey 可以计算 shard，但最终物理表必须重新映射成幂等表。
2. 有业务 route 时复用原始 CompositeShardKey + ShardRouteInfo，不重新 hash。
3. Business physical table 永远不能被 Idempotency SQL 直接使用。
4. DIRECT_DATASOURCE 只复用 dataSourceKey，不复用 business tableName。
5. Recovery 未绑定物理 shard 时 fail-fast。
6. Recovery 已绑定 shard 时复用 shardInfo 并映射幂等表。
7. 固定单库单表模式保持可用。
8. 一次 execute 只调用一次 StorageRouteResolver，所有状态操作共享该作用域。
9. Spring 本地事务中 Business SQL 与 markSuccess 使用同一条 transaction-bound Connection。
10. 10 库 × 每库 10 表的 100 个 shard 均可完成首次执行与重复回放。
