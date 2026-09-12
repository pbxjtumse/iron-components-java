# Storage Routing Component

> 组件定位：为 Iron Components 提供统一存储路由上下文，而不是重新实现 Apache ShardingSphere / MyCAT。

## 1. 为什么需要这个组件

在幂等、Outbox、任务恢复、消息表等场景中，技术组件记录经常需要和业务数据落到同一个分片，并参与同一个本地事务。

例如：

```text
business_order
iron_idempotency_record
iron_outbox
iron_task
```

如果业务记录落在 `order-db-1`，幂等记录却落在 `order-db-0` 或 `infra-db`，普通本地事务就无法保证一起提交、一起回滚。

Storage Routing Component 用来统一表达：

```text
本次业务请求对应哪个 dataSourceKey？
本次技术组件记录应该使用哪张逻辑表或物理表？
本次 SQL 是否必须携带 shardKey？
底层是直连多库、ShardingSphere-JDBC，还是 Proxy 模式？
```

## 2. 当前第一版提供什么

```text
storage-routing-api
    RouteContext
    ShardKey
    ShardValue
    CompositeShardKey
    StorageRoute
    ShardRouteInfo
    PhysicalStorageLocation
    StorageRouteMode
    resolver.StorageRouteResolver
    resolver.ShardResolver
    mapping.RouteMappingStrategy
    StorageRouteContext
    StorageRouteScope
    StorageRoutingException

storage-routing-core
    ThreadLocalStorageRouteContext
    FixedStorageRouteResolver
    HashShardResolver
    DefaultStorageRouteResolver
    ShardIdHashStorageRouteResolver
    GlobalTableIndexRouteMappingStrategy / LocalTableIndexRouteMappingStrategy
    RouteMappingStrategyFactory

storage-routing-integration-relational
    StorageRouteToSqlRouteBridge
    DefaultStorageRouteToSqlRouteBridge

storage-routing-starter
    StorageRoutingAutoConfiguration
    StorageRoutingProperties
```

`StorageRoute` 组合 `context`、`shardInfo`、`location`。`RouteContext` 保存场景、逻辑表、类型化分片键和扩展属性；
`StorageRouteContext` 负责访问调用链中的路由结果，两者职责不同。

单字段与复合字段统一使用 `CompositeShardKey`。`ShardResolver` 只计算分片，`RouteMappingStrategy` 只映射物理位置，
接口位于 API、实现位于 Core。路由输入统一为 `RouteContext`，分片输入统一为 `CompositeShardKey`。

Relational bridge 负责把 `DIRECT_DATASOURCE` 的 `StorageRoute` 转成 `SqlRoute`，同时暴露 Storage 拼 SQL 所需的物理表名。
Spring Boot starter 默认装配 `StorageRouteContext` 与 bridge；哈希 resolver 需要显式配置库表拓扑后启用。

示例配置：

```properties
xjtu.iron.storage-routing.resolver.enabled=true
xjtu.iron.storage-routing.resolver.data-source-prefix=order-db-
xjtu.iron.storage-routing.resolver.table-prefix=business_order
xjtu.iron.storage-routing.resolver.database-count=10
xjtu.iron.storage-routing.resolver.tables-per-database=10
xjtu.iron.storage-routing.resolver.data-source-index-width=2
xjtu.iron.storage-routing.resolver.table-index-width=2
xjtu.iron.storage-routing.resolver.table-index-mode=GLOBAL_TABLE_INDEX
```

模型字段、API 边界与使用限制见 [StorageRoute 模型](docs/design/03-storage-route-model.md)。

## 3. 当前第一版不做什么

- 不做 SQL 解析
- 不做 SQL 改写
- 不做跨库结果归并
- 不做分布式事务
- 不替代 Apache ShardingSphere / MyCAT
- 不直接操作 JDBC Connection
- 不让 Relational Access 反向依赖 Storage Routing

## 4. 与 Relational Access 的关系

```text
Storage Routing
    -> 决定去哪：dataSourceKey / tableName / shardKey

Relational Access
    -> 决定怎么执行：Connection / PreparedStatement / SQLException / transaction-bound Connection
```

## 5. 文档阅读顺序

```text
docs/README.md
    -> 文档入口

docs/design/00-component-boundary.md
    -> 组件边界：Storage Routing 负责去哪，Relational Access 负责怎么执行

docs/design/01-module-layout.md
    -> 模块规划：什么时候需要 spi / config / integration / starter

docs/sequence/01-storage-route-context.puml
    -> StorageRouteContext 在线程内传播路由

docs/sequence/02-storage-route-to-relational-access.puml
    -> StorageRoute 如何桥接到 Relational Access

docs/design/03-storage-route-model.md
    -> 类型化分片键、组合模型与稳定编码说明

docs/sequence/03-storage-route-resolution.puml
    -> 当前已实现的分片计算、物理映射和结果组装流程
```

## 6. 推进路线

```text
Phase 2.1：先建立 StorageRoute API 与 ThreadLocal 上下文
Phase 2.2：接 Relational Access，提供 StorageRoute -> SqlRoute 的桥接
Phase 2.3：Idempotency JDBC Storage 接 StorageRoute
Phase 2.4：第一优先级接入 ShardingSphere-JDBC
```

当前完成的是 Phase 2.1 的模型与解析链路，以及 Phase 2.2 的直连 Relational bridge / starter 基础。
后续仍需让 Idempotency JDBC Storage、Outbox Storage 等组件读取路由并拼装自己的物理表 SQL。
上下文传播本身不会改写 SQL、创建事务或替代中间件。

验证命令（仓库根目录）：

```bash
mvn -pl :storage-routing-starter -am test
```
