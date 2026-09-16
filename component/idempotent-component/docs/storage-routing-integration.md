# Idempotency 与 Storage Routing 集成设计及使用指南

> 本文描述 `feature/idempotent-v2-route-aware-storage` 的最终模型。当前尚未上线，因此直接删除旧 `shardKey` 协议，不保留过渡 API 和兼容字段。

## 1. 最终结论

幂等请求只描述幂等业务语义，不重复携带 Storage Routing 的分片模型：

```java
IdempotencyRequest {
    String key;
    String requestHash;
    String routeKey;
    String storeName;
    int scanBucket;
    String policyName;
}
```

`IdempotencyRequest` 不再包含 `long shardKey`，也不直接依赖 `CompositeShardKey`。物理路由只有两条路径：

1. 业务代码已经打开 `StorageRouteScope`：幂等组件复用当前 `StorageRoute`，不进行第二次 hash。
2. 当前没有业务路由：`DefaultIdempotencyRouteContextFactory` 使用幂等 `key` 构造 `CompositeShardKey`，一次性解析并绑定 `StorageRoute`。

因此不需要再增加 `StorageRoutedIdempotencyExecutor`，也不存在“请求 shardKey、显式 CompositeShardKey、外层 RouteContext”三套入口之间的优先级和冲突检查。业务始终注入并调用同一个 `IdempotencyExecutor`。

## 2. 模块边界

```text
idempotent-api/core
  └─ 幂等 key、状态机、owner/version、Recovery、结果策略

idempotent-provider-jdbc
  └─ JDBC 状态持久化与 IdempotencyJdbcRoute 抽象

idempotent-integration-storage-routing
  ├─ StorageRouteAwareIdempotencyExecutor
  ├─ DefaultIdempotencyRouteContextFactory
  └─ StorageRoutingIdempotencyJdbcRouteResolver

idempotent-starter
  └─ 自动装配 route-aware Executor、JDBC route resolver 和幂等表映射策略
```

`idempotent-api`、`idempotent-core` 和 Provider 不依赖 Storage Routing 类型。只有 integration 模块同时依赖两个组件，这样普通单库、Redis 或不使用分库分表的应用不会被迫理解 `CompositeShardKey`。

## 3. 三种不同的 key

| 名称 | 职责 | 是否决定物理分片 |
| --- | --- | --- |
| `IdempotencyRequest.key` | 同一次逻辑请求的唯一身份，重试时必须稳定 | 没有外层业务路由时，作为默认分片输入 |
| `routeKey` | 业务元数据与诊断信息，Recovery 时原样保留 | 否，不参与默认物理路由 |
| `scanBucket` | 在一个已经确定的物理 shard 内拆分 Recovery 扫描任务 | 否，绝不等于 shardId/tableIndex |

`storeName` 是逻辑存储域，例如 `payment`、`message-consume`，不是 Provider 名、数据库名或表名。

`IdempotencyStorageContext` 最终只有：

```java
IdempotencyStorageContext {
    String storeName;
    int scanBucket;
}
```

物理库表身份保存在执行期的 `StorageRouteContext` 中，不再复制到幂等请求、记录、Recovery Candidate、JDBC 列或 Redis Hash。

## 4. 一次执行为什么只解析一次路由

Starter 使用 `StorageRouteAwareIdempotencyExecutor` 装饰核心 Executor：

```text
IdempotencyRequest
        │
        ├─ 已有 StorageRoute ───────────────┐
        │                                   │
        └─ 无 StorageRoute                  │
             ↓                              │
   DefaultIdempotencyRouteContextFactory    │
             ↓ idempotency key              │
   StorageRouteResolver.resolve() 仅一次     │
             ↓                              │
   StorageRouteContext.open(route) ◀────────┘
             ↓
   tryAcquire → business callback → markSuccess/markFailed
             ↓
        close scope
```

作用域覆盖完整的 `execute()` 或 `recover()`。因此 Acquire、业务回调、完成写和失败写看到的是同一个路由事实，不会因为重复计算或配置变化而落到不同库。

路由创建、解析或绑定异常会映射为 `REPOSITORY_ERROR`；核心 Executor 自己的异常语义仍由核心状态机处理。

## 5. 为什么只能复用 shardInfo，不能复用业务表名

同一个 `ShardRouteInfo(56, db=5, localTable=6)` 可以映射到多个表族：

```text
business mapping     -> db_05.business_order_56
idempotency mapping  -> db_05.iron_idempotency_record_56
outbox mapping       -> db_05.iron_outbox_56
```

业务和幂等可以共享数据库分片，但不能共享物理表名。`StorageRoutingIdempotencyJdbcRouteResolver` 的处理规则是：

- 当前路由有 `shardInfo`：复用 `shardInfo`，通过幂等专属 `RouteMappingStrategy` 重新映射表名。
- 当前是 `DIRECT_DATASOURCE` 且没有 `shardInfo`：只复用 `dataSourceKey`，使用幂等配置的 `directTableName`。
- 当前没有路由：按幂等 key 得到 route，再按上述规则映射。

## 6. 业务使用方式

### 6.1 场景 A：业务不关心分片，直接按幂等 key 路由

这是消息消费、独立后台任务或业务表不要求和幂等表同 shard 时的最简方式。业务只构造幂等请求：

```java
IdempotencyRequest request = IdempotencyRequest.builder()
        .key("create-order:" + command.requestId())
        .requestHash(requestHasher.hash(command))
        .routeKey("merchant:" + command.merchantId())
        .storeName("order-create")
        .scanBucket(Math.floorMod(command.requestId().hashCode(), 128))
        .policyName("order-durable")
        .build();

IdempotencyResult<Order> result = idempotencyExecutor.execute(request, context -> orderService.create(command));
```

业务代码不需要创建 `CompositeShardKey`。外层没有路由时，默认 Factory 使用 `request.key` 路由。

### 6.2 场景 B：业务表和幂等表必须落到同一个 shard

这是推荐的同库本地事务路径。`CompositeShardKey` 属于业务/application 层，因为只有业务知道应按 `tenantId`、`merchantId`、`userId` 还是复合维度路由：

```java
CompositeShardKey shardKey = CompositeShardKey.of(
        ShardKey.of("tenant_id", command.tenantId()),
        ShardKey.of("merchant_id", command.merchantId()));

RouteContext routeContext = RouteContext.builder()
        .routeName("order-create")
        .logicalTable("business_order")
        .shardKey(shardKey)
        .build();

StorageRoute businessRoute = storageRouteResolver.resolve(routeContext);
try (StorageRouteScope ignored = storageRouteContext.open(businessRoute)) {
    IdempotencyRequest request = IdempotencyRequest.builder()
            .key("create-order:" + command.requestId())
            .requestHash(requestHasher.hash(command))
            .routeKey("merchant:" + command.merchantId())
            .storeName("order-create")
            .scanBucket(Math.floorMod(command.requestId().hashCode(), 128))
            .policyName("order-durable")
            .build();

    return idempotencyExecutor.execute(request, context -> orderRepository.insert(command));
}
```

此时 `StorageRouteAwareIdempotencyExecutor` 检测到已有路由，直接进入 delegate。JDBC route resolver 复用当前 `ShardRouteInfo`，但把业务表重新映射成幂等表；不会使用幂等 key 二次 hash。

### 6.3 场景 C：只按一个业务字段分片

单字段也使用 `CompositeShardKey`，保持 Storage Routing 输入协议统一：

```java
CompositeShardKey shardKey = CompositeShardKey.of(ShardKey.of("user_id", command.userId()));
```

不要把 `userId` 填回 `IdempotencyRequest` 作为另一个 shard 字段。它只存在于业务 `RouteContext`，幂等组件通过当前 `StorageRoute` 间接复用结果。

### 6.4 哪些代码属于业务，哪些由组件完成

| 工作 | 所属层 |
| --- | --- |
| 决定按 tenant/user/merchant/order 哪个字段分片 | 业务/application 层 |
| 构造业务 `CompositeShardKey` 和 `RouteContext` | 业务/application 层 |
| 打开业务 `StorageRouteScope` | 业务入口模板或应用服务 |
| 构造 `IdempotencyRequest` | 业务/application 层 |
| 无业务路由时按幂等 key 生成默认 RouteContext | 幂等 integration |
| 一次执行只解析并绑定一次 StorageRoute | 幂等 integration |
| 将共享 shardInfo 映射成幂等物理表 | 幂等 integration |
| owner/version CAS、状态机、结果回放 | 幂等 core/provider |

可以在业务模板层封装“解析业务路由 + 打开 scope + 调用 IdempotencyExecutor”，但不应把 tenant/merchant 等业务字段硬编码进技术组件。

## 7. Recovery 路由

`scanBucket` 只能缩小一个物理 shard 内的扫描范围，不能推导物理库表。正确模型是：

```text
physical shard enumeration × scanBucket enumeration
```

外部 Reliable Task 先枚举物理 shard，绑定对应 `StorageRoute`，再查询桶：

```java
for (StorageRoute shardRoute : physicalShardRoutes) {
    try (StorageRouteScope ignored = storageRouteContext.open(shardRoute)) {
        IdempotencyRecoveryQuery query = new IdempotencyRecoveryQuery(
                "order-create", "order", assignedBucket, deadline, batchSize);
        List<IdempotencyRecoveryCandidate> candidates = recoveryQueryService.findCandidates(query);
        // 每个 candidate 在同一物理 shard 作用域中 recover。
    }
}
```

真实分片模式下，如果 Recovery 没有绑定物理 shard，resolver 会 fail-fast，而不是错误地拿 `scanBucket` 猜表。`IdempotencyRecoveryRequest` 同样不携带 shardKey；它复用 Recovery 调度器已经打开的 route scope。

## 8. JDBC 与事务

`RoutedJdbcIdempotencyRepository` 只负责路由到对应的 `JdbcExecutionManager + physicalTable`，状态 SQL 仍集中在 `JdbcIdempotencyRepository`：

```text
IdempotencyRepository API
      ↓
RoutedJdbcIdempotencyRepository
      ↓
IdempotencyJdbcRouteResolver
      ↓
IdempotencyJdbcRoute(dataSourceKey, tableName)
      ↓
JdbcExecutionManagerResolver
      ↓
JdbcIdempotencyRepository
```

`SpringTransactionJdbcExecutionManager` 使用 `DataSourceUtils` 获取事务绑定连接。业务 SQL 与 `markSuccess` 要复用同一连接，必须同时满足：

1. 两者位于同一个 Spring 本地事务。
2. 两者的 `StorageRoute.dataSourceKey` 相同。
3. `TransactionManager` 与 `JdbcExecutionManager` 使用该 key 对应的同一个 `DataSource`。

组件不会把跨库操作伪装成单库原子事务。10 库模式下需要按 `dataSourceKey` 选择匹配的事务执行器。

## 9. 10 库 × 10 表配置

全局编号模式（`db_00` 为表 00-09，`db_01` 为表 10-19）：

```yaml
xjtu:
  iron:
    storage-routing:
      resolver:
        enabled: true
        data-source-prefix: db_
        database-count: 10
        tables-per-database: 10
        data-source-index-width: 2
        table-index-width: 2
        table-index-mode: GLOBAL_TABLE_INDEX
    idempotent:
      jdbc:
        routing:
          enabled: true
          logical-table: iron_idempotency_record
          table-prefix: iron_idempotency_record
```

库内编号模式（每个库都是表 00-09）只需切换：

```yaml
table-index-mode: LOCAL_TABLE_INDEX
```

固定单库单表仍使用：

```yaml
xjtu.iron.idempotent.jdbc.table-name: iron_idempotency_record
```

`logical-table` 是路由语义，`table-prefix` 是分片表前缀，`table-name` 是固定模式完整表名，三者不应混用。

## 10. 数据结构变更

本轮是未上线阶段的破坏性清理：

- 删除 `IdempotencyRequest.shardKey` 和 Builder 方法。
- 删除 `IdempotencyRecoveryRequest.shardKey`。
- 删除 `IdempotencyStorageContext.shardKey`。
- 删除 `IdempotencyRecord`、`IdempotencyRecoveryCandidate` 的 shardKey。
- 删除 JDBC `shard_key` 列和索引。
- 删除 Redis Hash 的 `shard_key` 字段并同步所有 Lua 参数及快照下标。

如果本地已有旧测试库或 Redis 测试数据，应重新建表并清理旧 key；当前不提供旧 schema 的在线迁移兼容逻辑。

## 11. 验证清单

1. 无业务 route 时，默认按幂等 key 计算一次 route。
2. 有业务 route 时复用原始 `CompositeShardKey + ShardRouteInfo`，不重新 hash。
3. Business physical table 永远不会被幂等 SQL 使用。
4. DIRECT_DATASOURCE 只复用 dataSourceKey，不复用 business tableName。
5. 一次 execute/recover 的所有状态操作共享同一个 route scope。
6. Recovery 未绑定物理 shard 时 fail-fast。
7. JDBC 与 Redis 不再保存或比较旧 shardKey。
8. 10 库 × 每库 10 表的 100 个 shard 均能完成首次执行和重复回放。
9. Spring 本地事务中 Business SQL 与 markSuccess 使用同一 transaction-bound Connection。

## 12. 后续阶段

当前顺序仍然是先稳定 Direct DataSource 全链路，再增加 ShardingSphere-JDBC Adapter。Adapter 应复用现有 `IdempotencyJdbcRouteResolver` 边界，不重新把业务分片字段塞回 `IdempotencyRequest`。JDBC SQL 迁移到 `RelationalTemplate` 也应作为独立阶段进行，避免把路由正确性、状态机和 SQL 执行机制一次性混改。
