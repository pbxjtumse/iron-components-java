# Storage Routing 文档

Storage Routing 统一“业务分片输入 -> 路由事实”的表达，不绑定 JDBC、MyBatis 或 JPA。

## 当前模块

```text
storage-routing-api
storage-routing-core
storage-routing-integration
  storage-routing-integration-shardingsphere-jdbc
  storage-routing-integration-shardingsphere-proxy
storage-routing-starter
```

## 三种模式

| 模式 | StorageRoute 保留的事实 | 后续执行 |
| --- | --- | --- |
| `DIRECT_DATASOURCE` | 物理 dataSourceKey、物理表、可选 shardInfo | 组件 Provider 选择物理 Access |
| `SHARDING_SPHERE_JDBC` | 逻辑表、分片键 | 进程内 ShardingSphere route/rewrite/execute |
| `PROXY` | 逻辑表、分片键 | Proxy 服务端 route/rewrite/execute |

Storage Provider 直接消费 `StorageRoute`，不再经过公共 `SqlRoute` bridge。这样 Storage Routing 只决定
“去哪里”，Relational Access 只管理“如何使用 MyBatis 和事务访问”。

ThreadLocal 嵌套作用域、异步不会自动传播等概念，见 [从零理解路由与作用域](从零理解路由与作用域.md)。

## 与 Relational Access、ShardingSphere 的边界

Storage Routing 的 `dataSourceKey` 需要结合模式理解：

| 模式 | `dataSourceKey` 的含义 | Access 数与物理库数的关系 |
| --- | --- | --- |
| `DIRECT_DATASOURCE` | 应用直连的物理 DataSource key | 通常一个物理库一个 Access |
| `SHARDING_SPHERE_JDBC` | 可选的应用侧逻辑 Access key | 一个逻辑 Access 可代理多个物理库 |
| `PROXY` | 可选的 Proxy 连接 Access key | 一个 Proxy Access 可通向多个后端库 |

`ShardingSphereJdbcStorageRouteResolver` 不创建 `ShardingSphereDataSource`。它只返回 middleware route，
真正的 ShardingSphere DataSource 由应用或 ShardingSphere 集成创建，再被 Relational Access 复用。

完整的 L0-L4 图、单库/Direct/JDBC/Proxy 执行流程和源码阅读路线见
[Relational Access V1 文档](../../relational-access-component/docs/README.md)。
