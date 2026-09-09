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
    StorageRoute
    StorageRouteMode
    StorageRouteRequest
    StorageRouteResolver
    StorageRouteContext
    StorageRouteScope
    StorageRoutingException

storage-routing-core
    ThreadLocalStorageRouteContext
    FixedStorageRouteResolver
    HashStorageRouteResolver
```

## 3. 当前第一版不做什么

- 不做 SQL 解析
- 不做 SQL 改写
- 不做跨库结果归并
- 不做分布式事务
- 不替代 Apache ShardingSphere / MyCAT
- 不直接操作 JDBC Connection

## 4. 与 Relational Access 的关系

```text
Storage Routing
    -> 决定去哪：dataSourceKey / tableName / shardKey

Relational Access
    -> 决定怎么执行：Connection / PreparedStatement / SQLException / transaction-bound Connection
```

## 5. 推进路线

```text
Phase 2.1：先建立 StorageRoute API 与 ThreadLocal 上下文
Phase 2.2：接 Relational Access，提供 StorageRoute -> SqlRoute 的桥接
Phase 2.3：Idempotency JDBC Storage 接 StorageRoute
Phase 2.4：第一优先级接入 ShardingSphere-JDBC
```
