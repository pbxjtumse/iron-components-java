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

## Recovery 扫描

分片场景不能在没有物理范围的情况下扫描全部分片。外部 Reliable Task 应枚举分片，
在每个 `StorageRouteScope` 中调用候选扫描；真正接管仍由 owner/version CAS 决定。
