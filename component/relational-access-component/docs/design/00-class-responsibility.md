# Relational Access v1 类职责总表

> 目标：让代码阅读者在进入实现前先知道每个类为什么存在、谁调用它、能否删除，以及它与业务模板/技术组件的边界。

## 1. 先记住四层

```text
Business Template
    ↓ 高层能力 API
Idempotency / Message / Transaction / Retry
    ↓ Storage Adapter
JdbcXXXStorage
    ↓ relational-api
RelationalTemplate
    ↓ relational-spi
ConnectionProvider / DataSourceResolver / ExceptionTranslator / Listener
    ↓
JDBC / Spring DataSourceUtils / DataSource
```

业务模板通常不直接依赖 `RelationalTemplate`；技术组件的 JDBC Storage Adapter 才是它的主要调用方。

---

## 2. relational-api：Storage Adapter 可以依赖的稳定 API

| 类 | 核心职责 | 典型调用方 | 是否可删 | 说明 |
|---|---|---|---|---|
| `RelationalTemplate` | query/update/batch 统一门面 | `JdbcIdempotencyStorage`、`JdbcOutboxStorage` | 否 | 组件最核心 API；调用方不接触 Connection/PreparedStatement 生命周期 |
| `SqlStatement` | 一次确定的 SQL 请求 | JDBC Storage | 否 | 持有 operationName、SQL、参数、options、route；SQL 仍属于 Storage |
| `BatchSqlStatement` | 同一 SQL + 多组参数 | Outbox/Task 批处理 | 可以合并但不建议 | 独立类型避免普通 SQL 与 batch 参数结构混淆 |
| `SqlParameter` | JDBC 位置参数 | Storage | 否 | 解决显式 JDBCType、NULL、敏感标记 |
| `SqlExecutionOptions` | Statement 级 timeout/fetchSize/maxRows | Storage | 否 | 不代表事务超时或业务超时 |
| `SqlRoute` | 已计算好的 dataSourceKey | Storage/Sharding Adapter | 否 | Relational 不算 shard；dataSourceKey 选库，物理表名进入最终 SQL |
| `RowMapper<T>` | 当前 ResultSet 行 -> T | Storage | 否 | 有意暴露 ResultSet；不再造 IronRow；mapper 不允许返回 null |
| `UpdateResult` | affectedRows | Storage | 否 | 幂等 CAS 最常用：affectedRows == 1 |
| `BatchResult` | JDBC batch updateCounts | Storage | 否 | 对数组做 defensive copy |
| `RelationalAccessException` | 对外统一异常 | Storage/Retry/Transaction | 否 | 不制造 DeadlockException 等大量异常子类 |
| `RelationalFailureType` | 稳定失败分类 | Storage/Retry | 否 | 上层不要依赖 MySQL vendorCode/SQLState |

### API 设计原则

1. SQL 不消失，SQL 属于具体 Storage Adapter。
2. API 不暴露 `ConnectionProvider`、`PreparedStatement`。
3. `SqlStatement.of(...)` 是普通场景入口；复杂 NULL/JDBCType 使用 `SqlParameter`。
4. `operationName` 必须稳定、低基数，例如 `idempotency.try-acquire`，不能放 orderId。
5. V1 不提供 generated-key API；基础组件优先使用上层生成的业务 ID。
6. `SqlRoute.dataSourceKey` 不是表名，它只用于选择 DataSource。

---

## 3. relational-spi：框架集成层才依赖的扩展契约

### `com.xjtu.iron.relational.spi.connection`

| 类 | 核心职责 | 谁实现/调用 | 是否可删 | 说明 |
|---|---|---|---|---|
| `ConnectionProvider` | 为一次 SQL 获取 ConnectionHandle | Core 调用；Default/Spring 实现 | 否 | Core 获取 Connection 的唯一入口 |
| `ConnectionHandle` | 暴露 Connection + 资源释放边界 | Provider 返回 | 否 | 关键不是包装 JDBC，而是明确物理 Connection 谁能关闭 |
| `ConnectionOwnership` | OWNED/BORROWED | Handle | 可改 boolean 但不建议 | enum 比 boolean 更明确；BORROWED 表示外部事务持有 |
| `DataSourceResolver` | dataSourceKey -> DataSource | ConnectionProvider 调用 | 否 | 不是分库分表算法；Sharding 已经提前算好路由 |

### `com.xjtu.iron.relational.spi.exception`

| 类 | 核心职责 | 谁实现/调用 | 是否可删 | 说明 |
|---|---|---|---|---|
| `SqlExceptionTranslator` | SQLException -> RelationalAccessException | Core 调用 | 否 | 上层稳定异常语义的关键扩展点；实现异常会被模板保护 |

### `com.xjtu.iron.relational.spi.execution`

| 类 | 核心职责 | 谁实现/调用 | 是否可删 | 说明 |
|---|---|---|---|---|
| `SqlExecutionContext` | 向 SPI 传递 operation/kind/sql/route/options | Core 创建 | 否 | 不携带高基数业务 ID，不携带参数值 |
| `SqlExecutionKind` | QUERY_ONE/UPDATE/BATCH 等执行类型 | Metrics/Listener | 否 | 不解析 SQL，只描述当前 API 动作 |
| `SqlExecutionListener` | before/success/failure 旁路观测 | Metrics/Trace/SlowSQL | 可以 NOOP | 回调异常不得改变 SQL 主链结果 |

### 已删除：`RelationalDialect` / `GeneratedKey<T>`

v1 没有分页 SQL 改写、Upsert DSL、SQL AST，也不需要方言。基础组件也不推荐依赖数据库自增主键，因此 generated-key API 暂不纳入稳定门面。

---

## 4. relational-core：真正执行 JDBC 的代码

| 类 | 核心职责 | 是否可删 | 为什么不继续拆 |
|---|---|---|---|
| `DefaultRelationalTemplate` | 固定完整 JDBC 生命周期；实现 RelationalTemplate API | 否 | 唯一主模板，避免 QueryExecutor/UpdateExecutor 等重复模板代码 |
| `SqlStatementValidator` | 结构校验 | 可以内联但保留 | 从主模板移走无聊校验，让主流程更可读；不解析 SQL |
| `JdbcParameterBinder` | SqlParameter -> PreparedStatement | 否 | NULL/JDBCType 绑定集中一次解决 |
| `JdbcStatementConfigurer` | timeout/fetchSize/maxRows -> PreparedStatement | 否 | Statement 级执行选项集中一次解决 |
| `DefaultConnectionProvider` | DataSourceResolver -> DataSource.getConnection() -> OWNED Handle | 否 | 非 Spring/纯 JDBC 模式的默认入口 |
| `DefaultConnectionHandle` | OWNED/BORROWED 基础句柄 | 否 | 默认 provider 使用 OWNED；未来其他 integration 也可参考 |
| `SingleDataSourceResolver` | 单 DataSource 默认解析 | 否 | 最简单启动方式；命名路由会 fail-fast |
| `RoutingDataSourceResolver` | 多 DataSource 映射解析 | 否 | dataSourceKey -> DataSource；不算 shard、不改写表名 |
| `StandardSqlExceptionTranslator` | 标准 JDBC/SQLState 保守分类 | 否 | 提供不绑定数据库厂商的 baseline；厂商细分以后扩展 |

### Single 与 Routing 的区别

```text
SingleDataSourceResolver
    defaultRoute -> defaultDataSource
    namedRoute   -> DATA_SOURCE_ROUTING_ERROR

RoutingDataSourceResolver
    defaultRoute             -> defaultDataSource
    SqlRoute.of("infra-db") -> routedDataSources.get("infra-db")
    unknown key              -> DATA_SOURCE_ROUTING_ERROR
```

---

## 5. relational-integration-spring：Spring/MyBatis 共享事务的关键桥

| 类 | 核心职责 | 是否可删 | 说明 |
|---|---|---|---|
| `SpringTransactionAwareConnectionProvider` | 使用 DataSourceUtils 获取当前 transaction-bound Connection | Spring 场景不能删 | 它**不创建事务**，只参与已有事务 |
| `SpringConnectionHandle` | 使用 DataSourceUtils 对称 release | 不能删 | 不能换成 `connection.close()`；事务绑定连接只能逻辑 release |

关键语义：

```text
TransactionExecutor(REQUIRED / REQUIRES_NEW)
        ↓ 建立事务并绑定 Connection
MyBatis -----------------------------┐
                                     ├→ same transaction-bound Connection
RelationalTemplate -> SpringProvider ┘
```

---

## 6. 与当前 idempotent JDBC 的迁移关系

当前幂等组件已有 `JdbcExecutionManager`：

```text
withConnection
inCurrentTransaction
inNewTransaction
```

它同时承担了“Connection 获取”和“事务模式编排”。Relational Access 引入后，建议逐步拆成：

```text
JdbcIdempotencyStorage
    ↓ SQL/参数/结果解释
RelationalTemplate
    ↓ Connection 生命周期
SpringTransactionAwareConnectionProvider

外层 Idempotency Transaction Integration
    ↓
TransactionExecutor(REQUIRED / REQUIRES_NEW)
```

也就是说：

- `PreparedStatement/ResultSet/参数绑定/异常翻译` 迁入 Relational Access；
- `Tx-A/Tx-B/Tx-C 为什么需要 REQUIRED/REQUIRES_NEW` 仍属于幂等 + transaction integration；
- 不应该把 `inNewTransaction()` 塞进 `RelationalTemplate`。

---

## 7. 推荐代码阅读顺序

```text
1. RelationalTemplate
2. SqlStatement / SqlParameter / SqlExecutionOptions / SqlRoute
3. DefaultRelationalTemplate
4. docs/code-walkthrough/DefaultRelationalTemplate-walkthrough.md
5. SqlStatementValidator
6. JdbcStatementConfigurer
7. JdbcParameterBinder
8. ConnectionProvider / ConnectionHandle
9. DefaultConnectionProvider / SingleDataSourceResolver / RoutingDataSourceResolver
10. SpringTransactionAwareConnectionProvider
11. SqlExceptionTranslator / StandardSqlExceptionTranslator
12. SqlExecutionListener / SqlExecutionContext
```

先理解一条 `update()`，再看 `queryOne()`、batch、多数据源路由，最快。
