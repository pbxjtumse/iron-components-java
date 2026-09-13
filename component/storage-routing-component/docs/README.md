# Storage Routing Component Docs

> 文档定位：记录 Storage Routing Component 的边界、分包、演进路线、关键流程和最小使用样例。

## 1. 当前阶段

当前处于：

```text
Phase 2.2：类型化单字段/复合字段 + 组合式 StorageRoute + 分片计算/物理映射编排
           + ThreadLocal 上下文 + Relational Access 直连桥接 + Spring Boot starter
```

当前目标不是一次性完成完整分库分表中间件，而是先定住：

```text
业务路由结果如何表达？
技术组件如何拿到同一个路由？
Relational Access 如何根据路由选择 DataSource？
ShardingSphere-JDBC / MyCAT / Direct Routing 如何作为底层实现替换？
```

## 2. 阅读顺序

```text
00-component-boundary.md
    先理解组件边界

01-module-layout.md
    再理解 api / core / spi / config / integration / starter 是否需要

02-basic-usage-examples.md
    再看 RouteContext、类型化分片键、逻辑表与上下文传播的代码样例

03-storage-route-model.md
    查看本轮模型调整、API 收敛和各字段的职责

01-storage-route-model.puml
    查看输入、类型化分片键与组合式结果的类关系

03-storage-route-resolution.puml
    查看当前代码真实执行的解析流程

01-storage-route-context.puml
    看 StorageRouteContext 如何保证一次调用链内路由一致

02-storage-route-to-relational-access.puml
    看 StorageRoute 如何转成 Relational Access 可执行的 SqlRoute
```

## 3. 当前代码模块

```text
storage-routing-api
storage-routing-core
storage-routing-integration
    storage-routing-integration-relational
storage-routing-starter
```

## 4. 当前最建议先看的代码

```text
storage-routing-api/src/main/java/com/xjtu/iron/storage/routing/api/RouteContext.java
    看调用方如何表达 routeName / logicalTable / CompositeShardKey

storage-routing-api/src/main/java/com/xjtu/iron/storage/routing/api/CompositeShardKey.java
    看字段顺序、字段名、类型化值和稳定编码

storage-routing-api/src/main/java/com/xjtu/iron/storage/routing/api/StorageRoute.java
    看 Resolver 最终输出什么路由结果

storage-routing-core/src/main/java/com/xjtu/iron/storage/routing/core/resolver/HashShardResolver.java
    看单字段历史落点规则和复合键如何得到 ShardRouteInfo

storage-routing-core/src/main/java/com/xjtu/iron/storage/routing/core/resolver/DefaultStorageRouteResolver.java
    看分片结果如何经过映射策略生成 StorageRoute

storage-routing-core/src/main/java/com/xjtu/iron/storage/routing/core/context/ThreadLocalStorageRouteContext.java
    看路由如何绑定到当前调用链

storage-routing-integration/storage-routing-integration-relational/src/main/java/com/xjtu/iron/storage/routing/integration/relational/DefaultStorageRouteToSqlRouteBridge.java
    看 DIRECT_DATASOURCE 路由如何转换成 Relational Access 的 SqlRoute

storage-routing-starter/src/main/java/com/xjtu/iron/storage/routing/starter/autoconfigure/StorageRoutingAutoConfiguration.java
    看 Spring Boot 下默认 Context、bridge 和可选 hash resolver 如何装配

storage-routing-core/src/test/java/com/xjtu/iron/storage/routing/core/resolver/HashShardResolverTest.java
    看类型化键的固定落点和同分片不同表
```

## 5. 后续模块规划

```text
storage-routing-spi
storage-routing-config
storage-routing-integration-shardingsphere
```

`storage-routing-integration-relational` 与 `storage-routing-starter` 已具备真实职责。
后续重点是让技术组件 Storage 接入当前路由，再基于同一模型增加 ShardingSphere-JDBC adapter。
