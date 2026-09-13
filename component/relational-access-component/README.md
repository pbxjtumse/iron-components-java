# Relational Access Component

> 当前阶段：**v1 JDBC Foundation / Usable Baseline**  
> 当前已经包含 `relational-api`、`relational-spi`、`relational-core`、`relational-integration` 与 `relational-starter`，并补齐 H2 主链测试、Spring 同事务回滚测试和多数据源路由测试。

## 1. 组件定位

Relational Access Component 是 Iron Components 面向关系型数据库的轻量访问底座。

它不替代 MyBatis、JPA、jOOQ，也不实现 ORM。它解决基础组件内部重复出现的 JDBC 基础设施问题：SQL 执行、参数绑定、结果映射、Connection 生命周期、DataSource 解析、异常翻译、事务 Connection 参与和可观测扩展。

```text
Business Template
    |
    +--> IdempotencyExecutor / MessageTemplate / TransactionExecutor
    |           |
    |           +--> JdbcXXXStorage
    |                     |
    |                     +--> RelationalTemplate
    |
    +--> Business Repository --> MyBatis / JPA / jOOQ

RelationalTemplate
    -> ConnectionProvider
        -> DefaultConnectionProvider
            -> SingleDataSourceResolver
            -> RoutingDataSourceResolver
        -> SpringTransactionAwareProvider
            -> DataSourceUtils / transaction-bound Connection
    -> JDBC
```

最重要的边界：

```text
Storage 决定：执行什么 SQL、参数是什么、结果如何解释。
Relational Access 决定：如何安全、统一地执行这条 SQL。
Transaction 决定：事务何时 begin / commit / rollback，以及 REQUIRED / REQUIRES_NEW 等传播语义。
Sharding / Routing 决定：dataSourceKey 和物理表名。
```

## 2. Maven modules

```text
relational-access-component
├── relational-api
├── relational-spi
├── relational-core
├── relational-integration
│   └── relational-integration-spring
├── relational-starter
└── docs
```

### relational-api

技术组件 JDBC Storage Adapter 的稳定公开 API：

- `RelationalTemplate`
- `SqlStatement` / `BatchSqlStatement`
- `SqlParameter`
- `SqlExecutionOptions`
- `SqlRoute`
- `RowMapper<T>`
- `UpdateResult` / `BatchResult`
- `RelationalAccessException` / `RelationalFailureType`

### relational-spi

只面向 Core / Integration：

- `connection.ConnectionProvider`
- `connection.ConnectionHandle` / `ConnectionOwnership`
- `connection.DataSourceResolver`
- `exception.SqlExceptionTranslator`
- `execution.SqlExecutionListener`
- `execution.SqlExecutionContext` / `SqlExecutionKind`

普通 Storage 不应直接拿这些 SPI。

### relational-core

纯 JDBC 主执行实现：

- `DefaultRelationalTemplate`
- `SqlStatementValidator`
- `JdbcParameterBinder`
- `JdbcStatementConfigurer`
- `DefaultConnectionProvider`
- `DefaultConnectionHandle`
- `SingleDataSourceResolver`
- `RoutingDataSourceResolver`
- `StandardSqlExceptionTranslator`

### relational-integration

框架集成聚合层，目前包含：

- `relational-integration-spring`

`relational-integration-spring` 负责 Spring 本地事务参与桥：

- `SpringTransactionAwareConnectionProvider`
- `SpringConnectionHandle`

它不 begin/commit/rollback；只通过 `DataSourceUtils` 获取和释放 transaction-bound Connection。

### relational-starter

单 DataSource / Primary DataSource 场景的默认自动装配：

```text
DataSource
    -> SingleDataSourceResolver
    -> SpringTransactionAwareConnectionProvider
    -> StandardSqlExceptionTranslator
    -> DefaultRelationalTemplate
```

多 DataSource 场景下，Starter 不猜测应该用哪个库；调用方提供自定义 `DataSourceResolver` 或 `ConnectionProvider` Bean 后，Starter 会继续补齐后续 Bean。

## 3. Spring Boot 使用

引入：

```xml
<dependency>
    <groupId>com.xjtu.iron</groupId>
    <artifactId>relational-starter</artifactId>
</dependency>
```

只要应用已有标准 `DataSource`，即可直接注入：

```java
private final RelationalTemplate relationalTemplate;
```

Storage 示例：

```java
UpdateResult result = relationalTemplate.update(
        SqlStatement.of(
                "idempotency.try-acquire",
                """
                UPDATE iron_idempotency
                   SET owner_token = ?, status = ?, updated_at = ?
                 WHERE idempotency_key = ?
                   AND status = ?
                """,
                ownerToken,
                PROCESSING,
                now,
                idempotencyKey,
                INIT
        )
);

return result.affectedRows() == 1;
```

Relational Access 不知道 `ACQUIRED / PROCESSING / ownerToken` 的含义；`affectedRows` 到幂等结果的解释仍属于 `JdbcIdempotencyStorage`。

## 4. update / batch 的真实含义

`RelationalTemplate.update(SqlStatement)` 对应 JDBC 的 `PreparedStatement.executeUpdate()` 执行路径。

它不只执行 SQL UPDATE。最终执行什么，由 `SqlStatement.sql()` 决定：

```text
INSERT INTO ...        -> 插入
UPDATE ...             -> 更新
DELETE FROM ...        -> 删除
MERGE INTO ...         -> 合并
INSERT ... ON DUPLICATE KEY UPDATE ... -> MySQL upsert
```

`RelationalTemplate.batchUpdate(BatchSqlStatement)` 和 `batch(BatchSqlStatement)` 对应 JDBC 的 `addBatch + executeBatch` 路径。

它也不区分 batch insert / batch update / batch delete。最终批量做什么，同样由 `BatchSqlStatement.sql()` 决定。

因此 Relational Access 不提供：

- `insertSelective`
- `updateByPrimaryKey`
- `updateByUniqueKey`
- `deleteById`
- `selectByCondition`
- `insertAndReturnKey`

这些需要表结构、列映射、主键/唯一键元数据，属于 MyBatis / MyBatis-Plus / JPA / 业务 Repository / Storage Adapter 的责任。

## 5. 单库与多库路由

`SqlRoute.dataSourceKey` 表示逻辑数据源标识，不是表名。

```text
SqlRoute.dataSourceKey  -> 选择 DataSource / 数据库连接池
SqlStatement.sql        -> 决定访问哪张表
```

单库场景使用 `SingleDataSourceResolver`：

```text
defaultRoute -> defaultDataSource
namedRoute   -> DATA_SOURCE_ROUTING_ERROR
```

多库场景使用 `RoutingDataSourceResolver`：

```java
DataSourceResolver resolver = new RoutingDataSourceResolver(
        infraDataSource,
        Map.of(
                "infra-db", infraDataSource,
                "order-db", orderDataSource,
                "archive-db", archiveDataSource
        )
);
```

然后 Storage 选择目标库：

```java
SqlStatement statement = SqlStatement.of(
        "idempotency.try-acquire",
        "UPDATE iron_idempotency SET status = ? WHERE idempotency_key = ?",
        PROCESSING,
        key
).withRoute(SqlRoute.of("infra-db"));
```

注意：Relational Access 仍然不计算 hash、不计算分片、不改写表名。未来分库分表组件应先算出 `dataSourceKey` 和物理表名，再把结果交给 Relational Access 执行。

真实数据库 URL、用户名、密码不要进入组件源码仓库。组件测试使用 H2 多内存库验证路由语义；生产环境由业务工程通过 Spring Bean、配置中心或 K8S Secret 注入 DataSource。

## 6. API 分级

### Level A：业务模板依赖高层技术能力

例如：

- `IdempotencyExecutor`
- `MessageTemplate`
- `RetryExecutor`
- `TransactionExecutor / TransactionTemplate`
- 业务自己的 Repository Port

业务模板默认不直接依赖 `RelationalTemplate`。

### Level B：技术组件 Storage Adapter 依赖 relational-api

例如：

```text
JdbcIdempotencyStorage
JdbcOutboxStorage
JdbcTaskStorage
```

它们负责：

```text
领域存储操作
  -> 最终 SQL
  -> 参数
  -> SqlRoute
  -> RelationalTemplate
  -> 解释 UpdateResult / 查询结果
```

### Level C：框架集成依赖 relational-spi

`relational-core`、Spring transaction integration、未来 observability/sharding integration 才依赖 `ConnectionProvider` 等 SPI。

## 7. SQL 的归属

SQL 仍然存在，但不散到 Relational Core。

```text
tryAcquire()                          幂等领域语义
    ↓
JdbcIdempotencyStorage               存储语义
    ↓
UPDATE ... WHERE status = ?          SQL / 物理表语义
    ↓
RelationalTemplate.update(...)       通用关系型执行语义
    ↓
PreparedStatement.executeUpdate()    JDBC 机制
```

Relational Access 不知道 `ownerToken / PROCESSING / Outbox / Order` 是什么。

## 8. 事务边界

Relational Core **不创建事务**。

```text
TransactionExecutor(REQUIRED / REQUIRES_NEW)
        ↓ 建立并绑定 Connection
MyBatis ------------------------------┐
                                      ├── same transaction-bound Connection
RelationalTemplate -> Spring Provider ┘
```

因此 Tx-A / Tx-B / Tx-C 的传播策略仍属于 idempotency + transaction integration，而不是 `RelationalTemplate`。

当前 `relational-integration-spring` 已用真实 H2 测试验证：同一个 Spring 本地事务内，`JdbcTemplate` 与 `RelationalTemplate` 的写入会一起提交或一起回滚。

## 9. v1 明确不做

- ORM / Entity / Repository 自动实现
- `@Table` / `@Column`
- SQL DSL / SQL AST / SQL Parser
- 自动分页 SQL 改写
- 自动重试
- 分库分表算法
- 在 relational-core 内 begin/commit/rollback
- 数据库连接池
- Schema Migration
- MySQL/PostgreSQL vendor-specific Translator（下一阶段按需求增加）
- 真实数据库连接信息管理

## 10. 验证命令

```bash
mvn -pl :relational-core,:relational-integration-spring,:relational-starter -am test
```

## 11. 代码和 UML 阅读入口

从 `docs/README.md` 开始。

最推荐顺序：

```text
class responsibility
  -> core class diagram
  -> DefaultRelationalTemplate walkthrough
  -> update flow
  -> queryOne flow
  -> batch flow
  -> multi datasource routing
  -> SQL lifecycle
  -> Connection ownership
  -> Spring shared transaction
  -> Idempotency Tx-A / Tx-B / Tx-C migration
```
