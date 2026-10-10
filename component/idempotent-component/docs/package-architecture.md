# Idempotent MyBatis 持久化分包

## 模块边界

| 模块 | 职责 |
| --- | --- |
| `idempotent-api` | 执行、Repository、Recovery、Policy 等稳定契约 |
| `idempotent-core` | 状态机、owner/version 决策、Tx-A/B/C 编排 |
| `idempotent-provider-mybatis` | Repository 实现、Mapper/XML、行映射、幂等表 schema |
| `idempotent-provider-redis` | WINDOWED Redis 实现 |
| `idempotent-integration-storage-routing` | 将幂等逻辑存储上下文解析为 DataSource/表 |
| `idempotent-integration-transaction` | Tx-B REQUIRED 协调；不再管理 JDBC Connection |
| `idempotent-starter` | Mapper、Repository、路由、事务与 Core 自动装配 |

## Provider 内部

```text
provider.mybatis
├── repository
│   ├── MyBatisIdempotencyRepository
│   └── RoutedMyBatisIdempotencyRepository
├── mapper
│   └── IdempotencyMapper
├── mapping
│   └── IdempotencyRow
└── routing
    ├── IdempotencyPhysicalRoute
    ├── IdempotencyPhysicalRouteResolver
    └── FixedIdempotencyPhysicalRouteResolver
```

Repository 拥有幂等 SQL 和状态迁移细节；`relational-mybatis` 只提供 `MyBatisAccess`。
这保证 Access 不反向理解幂等的 PROCESSING/SUCCESS/FAILED/DISCARDED 语义。

## 已删除的重复链路

- `idempotent-provider-jdbc`；
- `JdbcIdempotencyRepository`；
- `JdbcExecutionManager` 及其 Resolver；
- `SpringTransactionJdbcExecutionManager`；
- Idempotent 自己的 Direct JDBC 资源目录。

Direct 多数据源现在统一使用 Relational Access 的 `DirectMyBatisResourceRegistry`。
