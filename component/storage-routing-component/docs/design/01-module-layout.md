# 01. Module Layout

> 结论：当前已经具备 api / core / integration-relational / integration-shardingsphere-jdbc / integration-shardingsphere-proxy / starter。后续再按真实扩展需求补独立 SPI / Config。

## 1. 当前第一版模块

当前保留这些有真实职责的模块：

```text
storage-routing-api
storage-routing-core
storage-routing-integration
    storage-routing-integration-relational
    storage-routing-integration-shardingsphere-jdbc
    storage-routing-integration-shardingsphere-proxy
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

已经创建，并落地了 Direct DataSource、ShardingSphere-JDBC 与 ShardingSphere-Proxy 三种桥接。

当前结构：

```text
storage-routing-integration
    storage-routing-integration-relational
    storage-routing-integration-shardingsphere-jdbc
    storage-routing-integration-shardingsphere-proxy
```

说明：

```text
relational 定义 StorageRoute -> SqlRoute 的公共 bridge，并提供 Direct DataSource 实现
shardingsphere-jdbc 返回应用内逻辑 DataSource 与逻辑表名，物理路由由 ShardingSphere-JDBC 完成
shardingsphere-proxy 返回连接 Proxy 的 DataSource 与逻辑表名，物理路由由外部 Proxy 完成
```

后续结构建议：

```text
storage-routing-integration
    storage-routing-integration-mybatis
```

`DefaultStorageRouteToSqlRouteBridge` 只支持 `DIRECT_DATASOURCE`；`ShardingSphereJdbcStorageRouteToSqlRouteBridge`
只支持 `SHARDINGSPHERE_JDBC`；`ShardingSphereProxyStorageRouteToSqlRouteBridge` 只支持 `PROXY`。三种实现都遵循
`StorageRouteToSqlRouteBridge`，不会在 bridge 内解析或改写 SQL。

## 7. storage-routing-starter 是否需要

已经创建，但保持轻量。

当前自动装配：

```text
StorageRouteContext -> ThreadLocalStorageRouteContext
StorageRouteToSqlRouteBridge -> 按 mode 选择 Direct、ShardingSphere-JDBC 或 ShardingSphere-Proxy 实现
StorageRouteResolver -> Direct 仅在 resolver.enabled=true 时创建；JDBC / Proxy 按 mode 创建逻辑路由解析器
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
