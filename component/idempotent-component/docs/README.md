# Idempotent Component Docs

Idempotent 是 Iron Components 中文档结构最完整的组件之一，也是其他组件图形分层的参考基线。

## 推荐阅读顺序

1. [architecture.md](architecture.md)：ownerToken + version、Repository、StateMachine、Transaction、Recovery。
2. [core-flow.md](core-flow.md)：execute/recover 主链路。
3. [storage-routing-integration.md](storage-routing-integration.md)：Route-Aware Storage。
4. [transaction.md](transaction.md)：Tx-A / Tx-B / Tx-C。
5. [recovery.md](recovery.md)：显式 Recovery。
6. [code-reading-guide.md](code-reading-guide.md) 与 [executor-reading-guide.md](executor-reading-guide.md)。
7. [组件图](diagrams/component/L0-overview.puml)。
8. [L0 总览时序](diagrams/sequence/L0-overview.puml)。
9. L1-L4 场景时序。
10. [完整状态图](diagrams/state/00-idempotency-full-state.puml)。

## 当前代码关键类

- `DefaultIdempotencyExecutor`
- `StorageRouteAwareIdempotencyExecutor`
- `IdempotencyExecutionPreparer`
- `IdempotencyStateOperationExecutor`
- `IdempotencyBusinessExecutor`
- `DefaultIdempotencyStateMachine`
- `IdempotencyRepository / IdempotencyRecoveryRepository`
- `JdbcIdempotencyRepository / RoutedJdbcIdempotencyRepository`
- `RedisIdempotencyRepository`
- `IdempotencyTransactionCoordinator`

## 当前状态模型

持久状态只有：

`PROCESSING / SUCCESS / FAILED / DISCARDED`。

Acquire、Recovery、Write、Executor Result 都是不同层级的结果枚举，不能和持久状态混用。
