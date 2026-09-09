# 00. Component Boundary

> 结论：Storage Routing Component 负责“去哪”，Relational Access Component 负责“怎么执行”。

## 1. 为什么单独做 Storage Routing

Relational Access 已经解决了：

```text
Connection 获取与释放
PreparedStatement 执行
SQL 参数绑定
SQLException 翻译
Spring transaction-bound Connection 复用
```

但是它不应该解决：

```text
业务订单应该落哪个库？
幂等记录应该落哪个库？
Outbox 记录应该落哪个表？
十库百表时使用哪个 shardKey？
底层是 ShardingSphere-JDBC、MyCAT，还是直连多 DataSource？
```

这些问题属于 Storage Routing。

## 2. Storage Routing 负责什么

Storage Routing 负责产生统一路由结果：

```text
StorageRoute
    mode
    dataSourceKey
    tableName
    shardKeyName
    shardKeyValue
    attributes
```

它回答：

```text
本次数据访问应该去哪？
```

## 3. Storage Routing 不负责什么

Storage Routing 不做：

```text
SQL Parser
SQL Rewrite
Result Merge
JDBC Connection 管理
本地事务提交/回滚
分布式事务
```

这些能力分别属于 ShardingSphere / MyCAT / Relational Access / Transaction Component。

## 4. 与技术组件的关系

未来 Idempotency / Outbox / Task / Message Table 等技术组件应该共同使用同一份 StorageRoute。

```text
Business Request
    -> StorageRouteResolver
    -> StorageRouteContext
        -> Business Repository
        -> IdempotencyStorage
        -> OutboxStorage
        -> TaskStorage
```

核心目标是：

```text
业务数据和技术组件记录需要本地事务一致时，必须落到同一个物理事务资源。
```

这就是前面真实 MySQL 错库不回滚测试证明的问题。
