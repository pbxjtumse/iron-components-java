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
9. `state/00-sql-execution-lifecycle.puml`：DefaultRelationalTemplate 完整生命周期。
10. `state/01-connection-handle-lifecycle.puml`：OWNED/BORROWED 的真正意义。
11. `sequence/05-spring-shared-transaction.puml`：业务 MyBatis 与 RelationalTemplate 如何共享同一 Connection。
12. `sequence/06-idempotency-txabc-migration.puml`：当前幂等 JdbcExecutionManager 的 Tx-A/Tx-B/Tx-C 如何迁移。
13. `sequence/01-idempotency-storage-flow.puml`：幂等语义 -> Storage -> SQL -> Relational Access。

## 当前版本明确不做

- ORM Entity / Repository 自动生成
- SQL AST / Dynamic SQL DSL
- 自动分页 count SQL
- 分库分表算法
- 自动 Retry
- 在 Relational Core 内 begin/commit/rollback
- MySQL/PostgreSQL vendor-specific translator
- 数据库自增主键 generated-key API

## 当前最重要的边界

```text
Transaction Component / Integration
    -> 决定 REQUIRED / REQUIRES_NEW / commit / rollback

Relational Access
    -> 决定如何安全执行 SQL、如何拿/释放当前 Connection、如何翻译 JDBC 失败

JdbcXXXStorage
    -> 决定最终 SQL、参数、物理表、存储结果语义
```
