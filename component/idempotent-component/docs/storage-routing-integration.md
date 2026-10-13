# Idempotent 与 Storage Routing

## 分工

Storage Routing 产出“本次访问落在哪里”；Idempotent Provider 拥有“如何读写幂等表”的 Mapper/XML。

```text
IdempotencyStorageContext + key
            |
            v
StorageRoutingIdempotencyPhysicalRouteResolver
            |
            v
IdempotencyPhysicalRoute(dataSourceKey, tableName)
            |
            v
MyBatisAccessResolver -> MyBatisIdempotencyRepository
```

该链路不再经过 `SqlRoute` 或 `StorageRouteToSqlRouteBridge`。

## Direct

Direct 模式可以是固定单库，也可以是 HASH 分库分表。存在外层业务路由时，幂等表：

1. 复用外层 `shardInfo`，不重新 hash；
2. 使用幂等表自己的 `table-prefix` 重新映射物理表；
3. 校验映射后的 dataSourceKey 与外层路由相同；
4. 通过 `DirectMyBatisResourceRegistry` 同时取得该库的 MyBatis Access 和事务执行器。

```yaml
xjtu:
  iron:
    idempotent:
      mybatis:
        enabled: true
        table-name: iron_idempotency_record
        direct:
          enabled: true
        routing:
          enabled: true
          logical-table: iron_idempotency_record
          table-prefix: iron_idempotency_record
```

普通单库单表只需 `mybatis.table-name`，不开启 `mybatis.direct.enabled`。Direct 开关表示启用
Storage Routing 驱动的多物理 DataSource 资源目录，不是 MyBatis 的总开关。

## ShardingSphere-JDBC 与 Proxy

中间件模式不伪造物理库表。Resolver 保留逻辑表名和分片键，Idempotent 将幂等逻辑表名交给
MyBatis，由 ShardingSphere 继续 route/rewrite/execute。`sharding-sphere-jdbc.data-source-key` 或
`sharding-sphere-proxy.data-source-key` 只用于应用存在多个逻辑 DataSource 时选择 Access；为空时使用默认 Access。

这里的 key 不是 ShardingSphere 内部的物理 `ds_0` / `ds_1`。以 JDBC 模式为例：

```text
StorageRoutingIdempotencyPhysicalRouteResolver
  -> shardingSphereJdbcAccessKey
  -> IdempotencyPhysicalRoute.dataSourceKey
  -> MyBatisAccessResolver.resolve(key)
  -> SpringMyBatisAccess bound to logical ShardingSphere DataSource
  -> ShardingSphere selects physical shard
```

因此 `ShardingSphereDataSource` 由应用或 ShardingSphere 集成创建，不属于 Relational Access。Access 只复用
Spring 容器中的 DataSource 与 `SqlSessionTemplate`。一个逻辑 Access 后面可以是多个物理数据库。

当前 Adapter 也不会把 `StorageRouteContext` 自动转换成 ShardingSphere Hint。点查 SQL 必须携带中间件规则需要的
分片列；Recovery SQL 如果不能由查询条件确定目标分片，可能产生广播路由。详细架构、L0-L4 图和当前限制见
[Relational Access V1 文档](../../relational-access-component/docs/README.md)。

## Recovery 扫描

分片场景不能在没有物理范围的情况下扫描全部分片。外部 Reliable Task 应枚举分片，
在每个 `StorageRouteScope` 中调用候选扫描；真正接管仍由 owner/version CAS 决定。

上述定向扫描在 Direct 模式已经具有明确物理含义；在 ShardingSphere-JDBC/Proxy 模式，是否定向到单一物理分片
仍取决于 SQL 分片条件或未来的 Hint 集成，不能只依赖 `StorageRouteScope` 推断。
