# Idempotent Direct Storage E2E

该模块只验证 Idempotent Starter、Storage Routing 和公共 MyBatis Access 的 Direct 多数据源装配，
不包含生产代码。

## 装配约束

- `xjtu.iron.idempotent.mybatis.direct.enabled=true` 是多物理 DataSource 显式开关。
- DataSource Bean 名必须与 Storage Routing 产出的 `dataSourceKey` 一致。
- `DirectMyBatisResourceRegistry` 为每个物理数据源创建成对的 `MyBatisAccess` 和 `TransactionExecutor`。
- `StorageRouteAwareIdempotencyTransactionCoordinator` 在 Tx-B 使用当前绑定路由选择事务资源。
- 未注册的物理库、竞争的 Access/Transaction Resolver 或非 Direct 模式都应启动失败。

## 普通单库

单库单表不启用 Direct 开关；使用 `relational-starter` 从应用的唯一 DataSource 和
`SqlSessionTemplate` 创建默认 Access 即可。

Tx-A / Tx-B / Tx-C 的真实 H2 + MyBatis 原子性测试位于
`idempotent-provider-mybatis` 的 `MyBatisIdempotencyTransactionTest`。
