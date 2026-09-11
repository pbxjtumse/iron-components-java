# 01. Module Layout

> 结论：Storage Routing 最终需要 api / core / spi / config / integration / starter，但第一版不应该一次性创建所有空模块。

## 1. 当前第一版模块

当前保留两个模块：

```text
storage-routing-api
storage-routing-core
```

原因：当前阶段最重要的是先定住模型和最小实现。

## 2. storage-routing-api

职责：定义稳定编程模型。

包含：

```text
RouteContext
ShardValue
ShardKey
CompositeShardKey
StorageRoute
ShardRouteInfo
PhysicalStorageLocation
StorageRouteMode
resolver.StorageRouteResolver
resolver.ShardResolver
mapping.RouteMappingStrategy
StorageRouteContext
StorageRouteScope
StorageRoutingException
```

原则：

```text
api 只放调用方需要感知的概念。
api 不依赖 Spring、JDBC、ShardingSphere、MyCAT。
```

## 3. storage-routing-core

职责：提供默认实现。

当前包含：

```text
ThreadLocalStorageRouteContext
FixedStorageRouteResolver
HashShardResolver
DefaultStorageRouteResolver
ShardIdHashStorageRouteResolver
RouteMappingStrategyFactory
GlobalTableIndexRouteMappingStrategy / LocalTableIndexRouteMappingStrategy
```

原则：

```text
core 可以实现基础算法和上下文，但不绑定具体基础设施。
```

## 4. storage-routing-spi 是否需要

需要，但不建议现在马上创建空模块。

出现下面场景时再创建：

```text
不同底层路由实现需要统一扩展点
ShardingSphere Adapter / MyCAT Adapter / Direct Adapter 需要共同插件接口
需要 RouteDecorator / RouteListener / RouteBridge 一类扩展点
```

候选职责：

```text
StorageRouteBridge
StorageRouteAdapter
StorageRouteListener
StorageRouteMetadataProvider
```

## 5. storage-routing-config 是否需要

需要，但等规则模型明确后再创建。

它未来负责：

```text
dbCount
tableCount
dataSourcePrefix
tablePrefix
hashAlgorithm
routeMode
defaultRoute
```

当前 `ShardIdHashStorageRouteResolver` 通过 builder 构造，内部组合分片计算与物理映射，不急着抽配置模块。

## 6. storage-routing-integration 是否需要

需要，而且应该是后续重点。

未来结构建议：

```text
storage-routing-integration
    storage-routing-integration-relational
    storage-routing-integration-shardingsphere
    storage-routing-integration-mybatis
```

说明：

```text
relational 负责 StorageRoute -> SqlRoute
shardingsphere 负责 StorageRoute -> ShardingSphere Hint / logical datasource
mybatis 负责业务 Repository 与 RouteContext 协作
```

## 7. storage-routing-starter 是否需要

需要，但应该最后创建。

Starter 只有在这些东西稳定后才有价值：

```text
配置模型稳定
默认 Resolver 稳定
Context Bean 稳定
Integration Bean 稳定
```

否则 Starter 会很快反复推翻。

## 8. 推荐演进顺序

```text
Phase 2.1
    api + core + docs

Phase 2.2
    integration-relational
    StorageRoute -> SqlRoute

Phase 2.3
    idempotent-provider-jdbc 接 StorageRoute

Phase 2.4
    config + starter

Phase 2.5
    integration-shardingsphere
```
