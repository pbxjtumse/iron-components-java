# Cache Component Docs

Cache Component 统一缓存访问、多级缓存、TTL、空值策略、互斥加载、动态 CacheSpec、失效事件和观测。

## 当前模块

```text
cache-api
cache-core
cache-config
cache-provider
  caffeine
  redis
  composite
cache-integrations
  governance
  observability
cache-starter
cache-demo
```

当前代码已经包含 `CacheEventPublisher/Handler`、Redis Pub/Sub subscriber、动态 CacheSpec 抽象和 Micrometer 集成，不再把这些描述成“只有未来二期才存在”。

`DefaultCacheClient.evict` 先删除缓存，再通过统一路径发布一次携带 Trace 的失效事件。
删除失败时不发布；发布失败时按 `CacheEventPublishFailurePolicy.IGNORE/THROW` 处理，
并记录为 `PUBLISH_EVENT`，避免误报为缓存删除失败。

## 图形阅读顺序

1. [组件图](diagrams/component/00-component-overview.puml)
2. [L0 总览](diagrams/sequence/L0-overview.puml)
3. [L1 get + load 主流程](diagrams/sequence/L1-main-flow.puml)
4. [L2 多级缓存、降级、失效事件](diagrams/sequence/L2-scenario-flow.puml)
5. [L3 DefaultCacheClient 内部](diagrams/sequence/L3-internal-flow.puml)
6. [L4 Redis/Caffeine/Governance/Micrometer](diagrams/sequence/L4-integration-flow.puml)
7. [结果与访问状态图](diagrams/state/00-full-state.puml)

> `CacheResultStatus` 声明了 HIT / MISS_LOADED / EMPTY / ERROR / DEGRADED；当前 `DefaultCacheClient` 的静态工厂主要实际产生 HIT、MISS_LOADED、DEGRADED。文档不会把未使用的枚举值伪装成当前主链路必达状态。
