# Relational Access V1 执行流程

## 1. 公共主链路

所有 MyBatis 技术表 Provider 最终遵循同一条主链：

```text
Component Core
  -> Component Repository SPI
  -> Component MyBatis Repository
  -> resolve dataSourceKey + tableName
  -> MyBatisAccessResolver.resolve(dataSourceKey)
  -> MyBatisAccess.execute*(Mapper.class, callback)
  -> SqlSessionTemplate.getMapper(Mapper.class)
  -> Component Mapper XML
  -> DataSource
```

Access 只负责从 `SqlSessionTemplate` 获取 Mapper 并校验事务资源；表名、参数、CAS 条件仍由组件 Provider 决定。

## 2. 普通单库单表

Starter 看到唯一的 `DataSource` 和 `SqlSessionTemplate` 后创建：

```java
MyBatisAccess access = new SpringMyBatisAccess(
        dataSource,
        sqlSessionTemplate,
        transactionExecutor,
        listener
);
MyBatisAccessResolver resolver = FixedMyBatisAccessResolver.defaultDataSource(access);
```

组件路由返回 `dataSourceKey=null`，Resolver 使用默认 Access。普通单库不需要启用 Direct 资源目录。

## 3. Direct 多数据源

Direct 表示应用直接持有多个物理连接池。`DirectMyBatisResourceRegistry` 按 Spring Bean 名把每个
`DataSource` 注册为一个物理资源：

```java
Map<String, DataSource> dataSources = Map.of(
        "order-db-00", orderDb00,
        "order-db-01", orderDb01
);
DirectMyBatisResourceRegistry registry =
        DirectMyBatisResourceRegistry.fromDataSources(dataSources);
```

每个 key 同时拥有：

- 原始物理 `DataSource`；
- 基于该 DataSource 的 `SqlSessionTemplate`；
- 基于该 DataSource 的 `TransactionExecutor`；
- `SpringMyBatisAccess`。

一次访问的流程是：

1. `StorageRouteResolver` 根据 shard key 得到 `ShardRouteInfo` 与 `PhysicalStorageLocation`。
2. 技术组件用自己的表映射策略得到技术表物理表名。
3. Repository 用物理 `dataSourceKey` 调用 `RoutingMyBatisAccessResolver`。
4. Resolver 返回与该物理库配对的 Access。
5. Mapper XML 使用已校验的物理表名执行 SQL。
6. 若要求加入当前事务，`SpringMyBatisAccess` 校验线程上绑定的 Connection 确实属于该 DataSource。

因此必须先绑定路由、再开启事务。先在库 A 开事务、后把路由切到库 B 会被拒绝，而不是静默使用错误连接。

## 4. ShardingSphere-JDBC

ShardingSphere-JDBC 的流程有两级路由：

1. Iron Storage Routing 决定“这是 `SHARDING_SPHERE_JDBC` 模式”，保留逻辑表与 shard key。
2. `StorageRoutingIdempotencyPhysicalRouteResolver` 返回逻辑表，以及可选的**逻辑 Access key**。
3. `MyBatisAccessResolver` 选择承载 ShardingSphere 的应用侧 Access。
4. Mapper 生成包含逻辑表和分片列的 SQL。
5. ShardingSphere-JDBC 解析 SQL，再选择内部物理 DataSource、改写物理表并执行。

示意代码：

```java
StorageRoute route = StorageRoute.middleware(
        StorageRouteMode.SHARDING_SPHERE_JDBC,
        routeContext
);

IdempotencyPhysicalRoute physicalRoute = IdempotencyPhysicalRoute.of(
        "orders-sharding",          // Access key，不是 ds_0/ds_1
        "iron_idempotency_record"   // ShardingSphere 逻辑表
);
```

Access 不会看到 ShardingSphere 最终选择的 `ds_0` 或 `iron_idempotency_record_03`；那是 ShardingSphere
内部执行阶段的事实。

## 5. ShardingSphere-Proxy

Proxy 流程与 JDBC 模式在 Repository 之前相似，但边界不同：

1. `proxyAccessKey`（外部配置仍叫 `sharding-sphere-proxy.data-source-key`）选择一个普通 MyBatis Access。
2. 该 Access 的 DataSource URL 指向 ShardingSphere-Proxy。
3. Mapper 向 Proxy 发送逻辑 SQL。
4. Proxy 服务端解析、路由、改写和访问后端物理库。

应用内 Access 数量同样可以是 1，而 Proxy 后端可以连接多个物理数据库。

## 6. Idempotent 具体链路

以 `tryAcquire` 为例：

```text
RoutedMyBatisIdempotencyRepository.tryAcquire
  -> routeResolver.resolvePoint(storageContext, namespace, key)
  -> IdempotencyPhysicalRoute(dataSourceKey, tableName)
  -> accessResolver.resolve(dataSourceKey)
  -> cache/create MyBatisIdempotencyRepository(access, tableName)
  -> access.executeInNewTransaction("idempotency.tryAcquire", ...)
  -> IdempotencyMapper.selectForUpdate / insertProcessing / reacquire
```

`RoutedMyBatisIdempotencyRepository` 缓存的是“某个 Access + 某张表”的轻量 delegate，不缓存数据库连接。
连接生命周期仍由 Spring/MyBatis 管理。

`StorageRoutingIdempotencyPhysicalRouteResolver` 的分支规则：

| 当前路由 | 返回的 dataSourceKey | 返回的 tableName |
| --- | --- | --- |
| Direct 固定 | 物理库 key | 幂等固定物理表 |
| Direct 分片 | 复用业务 `shardInfo` 后重新映射出的物理库 key | 幂等表自己的物理分表 |
| ShardingSphere-JDBC | `sharding-sphere-jdbc.data-source-key`，可空 | 幂等逻辑表 |
| Proxy | `sharding-sphere-proxy.data-source-key`，可空 | 幂等逻辑表 |

Direct 分片不会直接复用业务表名。它只复用 `shardInfo`，再以幂等表的 `table-prefix` 重新映射，并校验
映射出的数据库与业务路由一致。

## 7. Tx-A / Tx-B / Tx-C

幂等 MyBatis Repository 对 Access 的使用如下：

| 阶段 | Access API | 目标 |
| --- | --- | --- |
| Tx-A | `executeInNewTransaction` | 短事务创建/抢占 `PROCESSING` |
| Tx-B | `executeInCurrentTransaction` | 业务 SQL 与 `SUCCESS` CAS 同一事务 |
| Tx-C | `executeInNewTransaction` | Tx-B 回滚后独立写入 `FAILED` |

Tx-B 成立要求业务 Mapper 与幂等 Mapper最终使用同一个 Spring 本地事务资源。使用相同框架不是充分条件；
即使都使用 MyBatis，路由到不同 DataSource 仍不能共享普通本地事务。

## 8. Recovery 扫描

Direct 模式下，外部可靠任务应显式枚举物理分片，在对应 `StorageRouteScope` 中扫描。这样
`resolveRecoveryRoutes` 能返回确定的物理 Access 与物理表。

ShardingSphere-JDBC/Proxy 模式下，V1 会把逻辑表交给中间件。是否精确命中某个物理分片，取决于 Recovery
SQL 是否包含中间件规则需要的分片条件，或未来是否增加 Hint 集成。`StorageRouteScope` 本身不是
ShardingSphere Hint，不能单靠线程上下文保证中间件定向路由。
