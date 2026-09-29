# Concurrency Component Docs

Concurrency 统一线程池、异步任务、超时、取消、fallback、上下文传播和任务生命周期观测。

## 当前模块

```text
concurrency-api
concurrency-config
concurrency-core
concurrency-integrations
concurrency-provider
concurrency-starter
concurrency-demo
```

## 当前真实主链路

`DefaultTaskExecutionTemplate` 负责把 `AsyncTask` 固化并投递；`TaskCommand` 负责原始任务生命周期；`DefaultTaskResultPipeline` 在原始 Future 之上追加 result timeout 和 fallback；`DefaultTaskLifecyclePublisher` 统一更新指标、Registry 和 Listener。

## 图形阅读顺序

1. [组件图](diagrams/component/00-component-overview.puml)
2. [L0 总览](diagrams/sequence/L0-overview.puml)
3. [L1 submit 主流程](diagrams/sequence/L1-main-flow.puml)
4. [L2 timeout / cancel / fallback](diagrams/sequence/L2-scenario-flow.puml)
5. [L3 TaskCommand + ResultPipeline](diagrams/sequence/L3-internal-flow.puml)
6. [L4 Spring / Micrometer / MDC](diagrams/sequence/L4-integration-flow.puml)
7. [完整任务状态图](diagrams/state/00-full-state.puml)

原有 `USER_GUIDE.md / QUICK_START.md / API_GUIDE.md / INTERNAL_DESIGN.md` 等专题文档继续保留，本目录新增图集作为统一代码阅读入口。
