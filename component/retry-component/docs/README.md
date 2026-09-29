# Retry Component Docs

当前代码基线：`retry-api / retry-core / retry-config / retry-demo`。

Retry 只负责进程内、同步、有限、可解释的重试，不负责持久化恢复、业务幂等、MQ ACK/NACK 或 Outbox。

## 核心代码

- `RetryExecutor / RetryExecution / RetryResult / RetryStatus`
- `RetryPolicy / RetryClassifier / RetryDecision`
- `BackoffStrategy / RetryDelay`
- `DefaultRetryExecutor / RetryEventDispatcher`
- `RetryClock / RetrySleeper`
- `RetryAutoConfiguration / RetryMetricsAutoConfiguration`

## 图形阅读顺序

1. [组件图](diagrams/component/00-component-overview.puml)
2. [L0 总览](diagrams/sequence/L0-overview.puml)
3. [L1 正常主流程](diagrams/sequence/L1-main-flow.puml)
4. [L2 终止与边界](diagrams/sequence/L2-scenario-flow.puml)
5. [L3 Executor 内部](diagrams/sequence/L3-internal-flow.puml)
6. [L4 Spring/Micrometer](diagrams/sequence/L4-integration-flow.puml)
7. [完整状态图](diagrams/state/00-full-state.puml)

当前真实终态来自 `RetryStatus`：`SUCCESS / EXHAUSTED / NOT_RETRYABLE / TIMED_OUT / INTERRUPTED / CANCELLED / ABORTED / EXECUTION_FAILED`。
