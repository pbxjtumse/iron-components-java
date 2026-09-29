# Observability Component Docs

Observability 当前代码已经形成 **Tracing + MDC + HTTP/AOP 接入** 的真实闭环基础。它不是 APM 后端，也不替代 OpenTelemetry Collector、Tempo、SkyWalking 或 Jaeger。

## 当前模块

```text
observability-api
observability-core
observability-otel
observability-starter
observability-demo-app
```

## 当前真实能力

- `ITraceService / ITraceSpan`：与具体追踪实现解耦的 API。
- `TraceTemplate`：start span、MDC、异常记录、close/restore 的统一模板。
- `TraceMdc`：统一使用 `trace_id / span_id` 并支持嵌套作用域恢复。
- `OtelTraceServiceImpl / OtelTraceSpanImpl`：OpenTelemetry Adapter。
- `TraceAspect`：注解方法级 tracing。
- `ObservabilityWebFilter`：HTTP 请求级 MDC。
- `ObservabilityAutoConfiguration`：Spring Boot 自动装配。

旧文档中“WebFilter / 方法级 MDC 还没有接入”的描述已经过时：这些类目前已经存在于代码中。Metrics 的统一抽象仍没有达到 Tracing 同等成熟度，因此不把规划中的 MetricsService 描述成已实现。

## 图形阅读顺序

1. [组件图](diagrams/component/00-component-overview.puml)
2. [L0 总览](diagrams/sequence/L0-overview.puml)
3. [L1 TraceTemplate](diagrams/sequence/L1-main-flow.puml)
4. [L2 error + MDC restore](diagrams/sequence/L2-scenario-flow.puml)
5. [L3 OTel span adapter](diagrams/sequence/L3-internal-flow.puml)
6. [L4 HTTP + Aspect + Agent/Collector 边界](diagrams/sequence/L4-integration-flow.puml)
7. [Span 生命周期](diagrams/state/00-full-state.puml)
