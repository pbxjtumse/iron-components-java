# Relational Access v1 Design Docs

当前文档与 `relational-api / relational-spi / relational-core / relational-integration / relational-starter` 代码一一对应。

## 推荐阅读顺序

1. `design/00-class-responsibility.md`：先知道每个类为什么存在、谁依赖、能不能删。
2. `component/00-relational-access-overview.puml`：整个 Iron Components 中的位置。
3. `component/01-api-spi-boundary.puml`：业务模板 / Storage / SPI 三层边界。
4. `component/02-core-class-diagram.puml`：当前真实代码类图。
5. `code-walkthrough/DefaultRelationalTemplate-walkthrough.md`：逐段理解核心模板执行链路。
6. `sequence/03-update-main-flow.puml`：先理解最简单也最重要的 update/CAS。
7. `sequence/00-query-one-flow.puml`：查询、RowMapper、NON_UNIQUE_RESULT。
8. `sequence/04-batch-update-flow.puml`：JDBC batch。
9. `sequence/07-multi-datasource-routing.puml`：多数据源场景下 dataSourceKey 如何选择 DataSource。
10. `sequence/08-same-shard-shared-transaction.puml`：同 shard 下业务 SQL 与幂等 SQL 如何共享本地事务。
11. `sequence/09-shardingsphere-jdbc-first-mode.puml`：ShardingSphere-JDBC 优先模式下的逻辑 DataSource 接入方式。
12. `design/01-sharding-component-roadmap.md`：后续 Sharding Component 的抽象边界与推进路线。
13. `state/00-sql-execution-lifecycle.puml`：DefaultRelationalTemplate 完整生命周期。
14. `state/01-connection-handle-lifecycle.puml`：OWNED/BORROWED 的真正意义。
15. `sequence/05-spring-shared-transaction.puml`：业务 MyBatis 与 RelationalTemplate 如何共享同一 Connection。
16. `sequence/06-idempotency-txabc-migration.puml`：当前幂等 JdbcExecutionManager 的 Tx-A/Tx-B/Tx-C 如何迁移。
17. `sequence/01-idempotency-storage-flow.puml`：幂等语义 -> Storage -> SQL -> Relational Access。

## 当前版本明确不做

- ORM Entity / Repository 自动生成
- SQL AST / Dynamic SQL DSL
- 自动分页 count SQL
- 分库分表算法
- 自动 Retry
- 在 Relational Core 内 begin/commit/rollback
- MySQL/PostgreSQL vendor-specific translator
- 数据库自增主键 generated-key API
- 真实数据库连接信息管理

## 当前最重要的边界

```text
Transaction Component / Integration
    -> 决定 REQUIRED / REQUIRES_NEW / commit / rollback

Relational Access
    -> 决定如何安全执行 SQL、如何拿/释放当前 Connection、如何翻译 JDBC 失败

JdbcXXXStorage
    -> 决定最终 SQL、参数、物理表、存储结果语义

Sharding / Routing Adapter
    -> 决定 dataSourceKey、逻辑表名/物理表名、shardKey 传递规范
```

## 多数据源阅读口诀

```text
SqlRoute.dataSourceKey 选择哪个 DataSource
SqlStatement.sql       决定访问哪个表
RoutingDataSourceResolver 只做 key -> DataSource 映射
```

## 同库同事务阅读口诀

```text
业务数据可以继续走 MyBatis / JdbcTemplate
技术组件记录可以继续走 RelationalTemplate
二者要共享事务，必须使用同一个目标 DataSource / 同一个 transaction-bound Connection
```

真实数据库 URL、用户名、密码不进入组件源码仓库。组件测试使用 H2 多内存库验证路由语义，生产环境由业务工程通过 Spring Bean / 配置中心 / Secret 注入 DataSource。
