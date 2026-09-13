# 01. Module Layout

> 结论：当前已经具备 api / core / integration-relational / starter。后续再按真实扩展需求补 spi / config / shardingsphere adapter。

## 1. 当前第一版模块

当前保留这些有真实职责的模块：

```text
storage-routing-api
storage-routing-core
storage-routing-integration
    storage-routing-integration-relational
storage-routing-starter
```

原因：模型、默认解析、Relational Access 直连桥接与 Spring Boot 默认装配已经能形成最小闭环。
仍不创建只有目录、没有稳定职责的 SPI / Config 模块。

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
dataSourceIndexWidth
tablePrefix
tableIndexWidth
hashAlgorithm
routeMode
defaultRoute
```

当前 `ShardIdHashStorageRouteResolver` 通过 builder 构造，内部组合分片计算与物理映射，不急着抽配置模块。
Spring Boot starter 当前只提供一层轻量 properties，用于装配默认 hash resolver，不代表已经沉淀出独立配置中心模型。

## 6. storage-routing-integration 是否需要

已经创建，并落地了 Relational Access 直连桥接。

当前结构：

```text
storage-routing-integration
    storage-routing-integration-relational
```

说明：

```text
relational 负责 StorageRoute -> SqlRoute，并向 Storage 暴露物理表名
```

后续结构建议：

```text
storage-routing-integration
    storage-routing-integration-shardingsphere
    storage-routing-integration-mybatis
```

`storage-routing-integration-relational` 当前只支持 `DIRECT_DATASOURCE`。`SHARDINGSPHERE_JDBC` 与 `PROXY`
需要专门 adapter 决定是走统一逻辑 DataSource、Hint，还是代理入口。

## 7. storage-routing-starter 是否需要

已经创建，但保持轻量。

当前自动装配：

```text
StorageRouteContext -> ThreadLocalStorageRouteContext
StorageRouteToSqlRouteBridge -> DefaultStorageRouteToSqlRouteBridge
StorageRouteResolver -> 仅在 resolver.enabled=true 时按显式库表拓扑创建
```

默认 resolver 不自动启用，因为 databaseCount、tablesPerDatabase、dataSourcePrefix、tablePrefix
都属于业务路由规则，框架不应该猜。

## 8. 推荐演进顺序

```text
Phase 2.1
    api + core + docs

Phase 2.2
    integration-relational
    StorageRoute -> SqlRoute
    storage-routing-starter

Phase 2.3
    idempotent-provider-jdbc 接 StorageRoute

Phase 2.4
    outbox / task / message storage 按同一模型接入

Phase 2.5
    integration-shardingsphere
```
