# Relational Access v1 Design Docs

Relational Access 统一 JDBC SQL 执行、Connection 获取/释放、异常翻译与监听器生命周期。它**不负责事务 begin/commit/rollback，也不负责计算分片**。

当前代码模块：

```text
relational-api
relational-spi
relational-core
relational-integration
relational-starter
```

## L0-L4 阅读入口

| 层级 | 图 | 重点 |
|---|---|---|
| Component | [00-relational-access-overview](component/00-relational-access-overview.puml) | 在 Iron Components 中的位置 |
| L0 | [L0-overview](sequence/L0-overview.puml) | Caller -> RelationalTemplate -> JDBC |
| L1 | [L1-main-flow](sequence/L1-main-flow.puml) | query/update 正常执行 |
| L2 | [L2-scenario-flow](sequence/L2-scenario-flow.puml) | SQL 异常、RowMapper、非唯一结果 |
| L3 | [L3-internal-flow](sequence/L3-internal-flow.puml) | DefaultRelationalTemplate 内部生命周期 |
| L4 | [L4-integration-flow](sequence/L4-integration-flow.puml) | Storage Routing / Spring Transaction / ShardingSphere 边界 |
| State | [SQL lifecycle](state/00-sql-execution-lifecycle.puml) | SQL 执行生命周期 |
| State | [Connection handle](state/01-connection-handle-lifecycle.puml) | OWNED / BORROWED |

## 详细专题仍保留

- `design/00-class-responsibility.md`
- `code-walkthrough/DefaultRelationalTemplate-walkthrough.md`
- `sequence/05-spring-shared-transaction.puml`
- `sequence/07-multi-datasource-routing.puml`
- `sequence/08-same-shard-shared-transaction.puml`
- `sequence/09-shardingsphere-jdbc-first-mode.puml`
- `design/02-real-mysql-integration-test.md`

## 当前边界校正

早期文档把 Sharding 描述成“未来组件”。当前仓库已经存在独立 `storage-routing-component`，并已有 Direct、ShardingSphere-JDBC、ShardingSphere-Proxy Adapter。

因此现在的边界是：

```text
Storage Routing
    -> 决定 StorageRoute 与 logical/physical routing mode

StorageRouteToSqlRouteBridge
    -> 把 StorageRoute 转成 Relational Access 可执行的 SqlRoute / table name

Relational Access
    -> 获取 ConnectionHandle、执行 SQL、翻译 SQLException

Transaction Component / Spring integration
    -> 决定 begin / commit / rollback 与 transaction-bound Connection
```

`DefaultRelationalTemplate` 当前真实生命周期为：
validate -> context -> listener.before -> acquire ConnectionHandle -> prepare/configure/bind -> execute/map -> close resources/handle -> listener success/failure。
