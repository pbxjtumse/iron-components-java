# Idempotent 代码阅读路线

1. `DefaultIdempotencyExecutor`：理解 execute/recover 编排。
2. `DefaultIdempotencyStateMachine`：理解 Repository 结果到 EXECUTE/REPLAY/RETURN 的决策。
3. `IdempotencyTransactionCoordinator`：理解 Tx-B 边界。
4. `RoutedMyBatisIdempotencyRepository`：理解物理路由到 Access 的选择。
5. `MyBatisIdempotencyRepository`：理解 Tx-A/Tx-C、owner/version CAS 和恢复查询。
6. `IdempotencyMapper.xml`：最后对照唯一键、行锁和条件更新 SQL。
7. `SpringMyBatisAccess`：理解为什么 Mapper 能复用当前事务并安全开启 `REQUIRES_NEW`。

先看 API/Core，再看 Provider SQL；不要从 Starter Bean 倒推幂等语义。
