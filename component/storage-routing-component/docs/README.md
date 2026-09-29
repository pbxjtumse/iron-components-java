# Storage Routing Component Docs

Storage Routing 统一“业务分片输入 -> 路由事实 -> Relational Access”的表达。它不是数据库中间件，也不重新实现 ShardingSphere。

## 当前阶段（以 master 代码为准）

当前已经具备：

```text
类型化 ShardKey / CompositeShardKey
+ ShardResolver
+ RouteMappingStrategy
+ StorageRoute
+ ThreadLocal StorageRouteContext
+ Relational Access bridge
+ DIRECT_DATASOURCE
+ SHARDINGSPHERE_JDBC Adapter
+ SHARDINGSPHERE_PROXY Adapter
+ Spring Boot starter
```

因此旧文档中“后续再增加 ShardingSphere-JDBC / Proxy Adapter”的描述已经过时。当前下一阶段重点是**在线扩容/迁移、路由版本和生产验证**，而不是再证明 Adapter 是否存在。

## 当前模块

```text
storage-routing-api
storage-routing-core
storage-routing-integration
  storage-routing-integration-relational
  storage-routing-integration-shardingsphere-jdbc
  storage-routing-integration-shardingsphere-proxy
storage-routing-starter
```

## 三种模式

| StorageRouteMode | Iron 输出 | 后续物理路由 |
|---|---|---|
| DIRECT_DATASOURCE | 物理 dataSourceKey + 物理表 | Iron/应用 DataSource |
| SHARDINGSPHERE_JDBC | ShardingSphere 逻辑 DataSource + 逻辑表 | 进程内 ShardingSphere-JDBC |
| PROXY | 指向 Proxy 的 JDBC DataSource + 逻辑表 | ShardingSphere-Proxy |

JDBC/Proxy Adapter 不创建规则、不改写业务 SQL、不部署 ShardingSphere。

## 图形阅读顺序

1. [组件图](component/00-storage-routing-overview.puml)
2. [模型图](component/01-storage-route-model.puml)
3. [L0 总览](sequence/L0-overview.puml)
4. [L1 Direct Resolver](sequence/L1-main-flow.puml)
5. [L2 Scope](sequence/L2-scenario-flow.puml)
6. [L3 Resolver 内部](sequence/L3-internal-flow.puml)
7. [L4 Direct/JDBC/Proxy Integration](sequence/L4-integration-flow.puml)
8. [Route Scope 状态](state/00-full-state.puml)
9. [扩容开发计划](design/04-storage-expansion-development-plan.md)

## 当前最重要的代码

- `DefaultStorageRouteResolver`
- `HashShardResolver`
- `DefaultRouteMappingStrategy`
- `ThreadLocalStorageRouteContext`
- `DefaultStorageRouteToSqlRouteBridge`
- `ShardingSphereJdbcStorageRouteResolver / Bridge`
- `ShardingSphereProxyStorageRouteResolver / Bridge`
- `StorageRoutingAutoConfiguration`

## 核心边界

`DefaultStorageRouteResolver` 只做两步：`ShardResolver.resolve(CompositeShardKey)` 得到 `ShardRouteInfo`；`RouteMappingStrategy.map(shardInfo)` 得到 `PhysicalStorageLocation`。它不持有线程状态。

`StorageRouteContext` 只绑定已经算好的 route；Scope 的嵌套只表示上下文恢复，不代表跨库事务。

ShardingSphere Adapter 只改变“路由结果如何交给后续 SQL 层”，不改变业务 `RouteContext / CompositeShardKey` 协议。
