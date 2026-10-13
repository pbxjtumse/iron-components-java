# Relational Access V1 文档

当前版本已收口为 MyBatis-first 技术表接入层。它统一技术组件使用 MyBatis、Spring
本地事务、多 DataSource 选择与访问观测的方式，但不拥有业务 SQL，也不创建应用的连接池。

## 推荐阅读顺序

1. [architecture.md](architecture.md)：组件边界、三种部署模式，以及 `ShardingSphereDataSource` 到底归谁管理。
2. [execution-flow.md](execution-flow.md)：单库、Direct 多库、ShardingSphere-JDBC、Proxy 和幂等接入的完整流程。
3. [code-reading-guide.md](code-reading-guide.md)：从 Starter、Resolver、Access 到具体 Mapper/XML 的源码路线。
4. [L0 系统上下文图](diagrams/component/L0-system-context.puml)：先看组件之间的责任边界。
5. [L1 Access 选择](diagrams/sequence/L1-access-selection.puml)：理解 `dataSourceKey` 选择的是 Access，不一定是物理库。
6. L2 执行图：[Direct](diagrams/sequence/L2-direct-execution.puml)、[ShardingSphere-JDBC](diagrams/sequence/L2-shardingsphere-jdbc.puml)、[Proxy](diagrams/sequence/L2-shardingsphere-proxy.puml)。
7. [L3 Idempotent 集成](diagrams/sequence/L3-idempotent-route.puml)：理解 `StorageRoutingIdempotencyPhysicalRouteResolver`。
8. [L4 事务与分片边界](diagrams/sequence/L4-transaction-boundary.puml)：理解同库事务成立的条件和当前限制。

组件概览、模块清单与接入约束见 [组件 README](../README.md)。Storage Routing 的路由模型与作用域见
[Storage Routing 文档](../../storage-routing-component/docs/README.md)。

旧 `RelationalTemplate` 设计文档已随原生 JDBC 执行链一同删除，避免将已下线 API 当成现行用法。

各组件仍然保留自己的业务持久化代码：

- Repository SPI
- MyBatis Mapper
- Mapper XML
- 表结构脚本
- 状态机和 CAS 语义

但不再分别处理这些通用问题：

- 获取和管理 JDBC Connection
- PreparedStatement 参数绑定
- ResultSet 映射基础设施
- Spring 事务连接复用
- REQUIRED、REQUIRES_NEW 等事务执行方式
- 多数据源选择
- Storage Routing 物理数据源解析
- SQL 异常转换
- 重复键异常识别
- 访问监听与可观测性

Repository 的稳定扩展关系如下：

```text
reliable-task-core
  -> ReliableTaskRepository
       -> MyBatisReliableTaskRepository (V1)
       -> JdbcReliableTaskRepository    (未来可重新实现)
       -> JpaReliableTaskRepository     (未来可实现)
```

因此 V1 统一 MyBatis 并不等于永久锁死 MyBatis。Core 依赖 Repository SPI；未来增加
JDBC 或 JPA Provider 时，应新增 Provider 实现，而不是改变 Core 或复活一套与 MyBatis
并行的公共 SQL 模板。
