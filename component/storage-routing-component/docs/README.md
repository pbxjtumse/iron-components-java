# Storage Routing Component Docs

> 文档定位：记录 Storage Routing Component 的边界、分包、演进路线、关键流程和最小使用样例。

## 1. 当前阶段

当前处于：

```text
Phase 2.1：StorageRoute API + ThreadLocal Route Context
```

当前目标不是一次性完成完整分库分表中间件，而是先定住：

```text
业务路由结果如何表达？
技术组件如何拿到同一个路由？
Relational Access 后续如何根据路由选择 DataSource？
ShardingSphere-JDBC / MyCAT / Direct Routing 如何作为底层实现替换？
```

## 2. 阅读顺序

```text
00-component-boundary.md
    先理解组件边界

01-module-layout.md
    再理解 api / core / spi / config / integration / starter 是否需要

02-basic-usage-examples.md
    再看 scene / logicalTable / shardKey / route context 的代码样例

01-storage-route-context.puml
    看 StorageRouteContext 如何保证一次调用链内路由一致

02-storage-route-to-relational-access.puml
    看 StorageRoute 后续如何转成 Relational Access 可执行的 SqlRoute
```

## 3. 当前代码模块

```text
storage-routing-api
storage-routing-core
```

## 4. 当前最建议先看的代码

```text
storage-routing-api/src/main/java/com/xjtu/iron/storage/routing/api/StorageRouteRequest.java
    看调用方如何表达 logicalTable / scene / shardKey

storage-routing-api/src/main/java/com/xjtu/iron/storage/routing/api/StorageRoute.java
    看 Resolver 最终输出什么路由结果

storage-routing-core/src/main/java/com/xjtu/iron/storage/routing/core/resolver/HashStorageRouteResolver.java
    看第一版如何根据 shardKeyValue 计算 dataSourceKey 和 tableName

storage-routing-core/src/main/java/com/xjtu/iron/storage/routing/core/context/ThreadLocalStorageRouteContext.java
    看路由如何绑定到当前调用链

storage-routing-core/src/test/java/com/xjtu/iron/storage/routing/core/StorageRoutingUsageExampleTest.java
    看完整使用样例
```

## 5. 后续模块规划

```text
storage-routing-spi
storage-routing-config
storage-routing-integration
storage-routing-starter
```

这些模块会在扩展点和接入场景明确后再创建，避免第一版出现只有目录、没有真实职责的空壳模块。
