# Governance Component Docs

Governance Component 统一下游调用的 timeout、rate limit、bulkhead、circuit breaker、retry、fallback、异常映射和治理事件。

## 当前模块

```text
governance-api
governance-model
governance-core
governance-spi
governance-configs
governance-runtime
governance-engine
governance-integration
governance-observability
governance-extensions
governance-starters
governance-demo
```

当前可见 Engine 实现为 `Resilience4jGovernanceEngine`。Core 不直接依赖 Resilience4j；具体治理框架位于 engine 层。

## 当前执行顺序

Resilience4j Engine 当前有效调用顺序为：

```text
Timeout
  -> RateLimiter
  -> Bulkhead
  -> CircuitBreaker
  -> Retry
  -> Business Invocation
```

该顺序来自当前装饰器构造代码，不是抽象层写死的行业规则。

## 图形阅读顺序

1. [组件图](diagrams/component/00-component-overview.puml)
2. [L0 总览](diagrams/sequence/L0-overview.puml)
3. [L1 主调用](diagrams/sequence/L1-main-flow.puml)
4. [L2 failure / fallback](diagrams/sequence/L2-scenario-flow.puml)
5. [L3 Core 内部](diagrams/sequence/L3-internal-flow.puml)
6. [L4 Resilience4j / Spring AOP](diagrams/sequence/L4-integration-flow.puml)
7. [Iron 调用生命周期](diagrams/state/00-full-state.puml)

CircuitBreaker 的 CLOSED / OPEN / HALF_OPEN 是 Resilience4j Provider 管理的状态，不冒充 Governance Core 自己的状态机。
