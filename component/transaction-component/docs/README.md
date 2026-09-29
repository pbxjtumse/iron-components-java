# Transaction Component Docs

Transaction Component 只提供**本地事务统一执行抽象**。当前代码通过 `TransactionExecutor -> TransactionProvider -> SpringTransactionProvider` 对接 Spring `PlatformTransactionManager`。

## 核心代码

- `TransactionExecutor / TransactionCallback / TransactionOptions`
- `TransactionPropagation / TransactionIsolation`
- `TransactionContext`
- `TransactionOutcome / TransactionStage`
- `DefaultTransactionExecutor`
- `TransactionProvider`
- `SpringTransactionProvider`

## 图形阅读顺序

1. [组件图](diagrams/component/00-component-overview.puml)
2. [L0 总览](diagrams/sequence/L0-overview.puml)
3. [L1 REQUIRED 主流程](diagrams/sequence/L1-main-flow.puml)
4. [L2 REQUIRES_NEW / rollback](diagrams/sequence/L2-scenario-flow.puml)
5. [L3 Core -> Provider](diagrams/sequence/L3-internal-flow.puml)
6. [L4 Spring Transaction 集成](diagrams/sequence/L4-integration-flow.puml)
7. [完整状态图](diagrams/state/00-full-state.puml)

旧的 `design-v1.md / flow-v1-detailed.md` 继续保留作为设计背景；本图集以当前 V1.3 代码为准。

## 当前关键边界

正常成功不需要公共 `COMMITTED` outcome；当前 `TransactionOutcome` 只表达异常场景：

`ROLLED_BACK / COMMIT_UNKNOWN / FAILED`。

`VALIDATE / RESOLVE / BEGIN / EXECUTE / COMMIT / ROLLBACK / COMPLETION` 来自 `TransactionStage`。
