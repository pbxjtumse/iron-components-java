# observability-component

统一 Trace / MDC / Spring Boot 观测接入层。

当前实现重点已经不是“只有规划”，而是已经存在：

- `ITraceService / ITraceSpan` 抽象；
- `TraceTemplate`；
- `TraceMdc`；
- OpenTelemetry Adapter；
- `@Trace + TraceAspect`；
- `ObservabilityWebFilter`；
- Spring Boot AutoConfiguration；
- Demo App。

组件不替代 OpenTelemetry Collector、Tempo、SkyWalking、Jaeger、Prometheus 或 Grafana。后端平台和 Agent/Collector 部署属于运行环境。

Metrics 统一抽象仍是后续演进方向，当前不要把规划中的 MetricsService 当成已经存在的代码。

详细设计与 L0-L4 图集见：[docs/README.md](docs/README.md)。
