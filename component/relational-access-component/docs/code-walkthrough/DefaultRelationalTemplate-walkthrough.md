# DefaultRelationalTemplate 源码走读

> 目标：把 `DefaultRelationalTemplate` 这条主链路讲清楚。生产代码里只保留关键注释，逐行解释放在本文档，避免核心类被注释淹没。

## 1. 这个类到底是什么

`DefaultRelationalTemplate` 是 `RelationalTemplate` 的默认 JDBC 实现。

它不是 ORM，不知道实体、表结构、主键、唯一键，也不负责事务传播。它只负责把上层已经准备好的：

```text
SqlStatement / BatchSqlStatement
```

执行成真实 JDBC 调用：

```text
Connection
PreparedStatement
ResultSet
UpdateResult / BatchResult / Optional<T> / List<T>
```

完整生命周期是：

```text
validate
  -> build SqlExecutionContext
  -> listener.beforeExecute
  -> acquire ConnectionHandle
  -> prepare PreparedStatement
  -> configure Statement
  -> bind parameters
  -> execute SQL
  -> map result / build result
  -> close ResultSet / PreparedStatement
  -> close ConnectionHandle
  -> listener.afterSuccess / afterFailure
  -> translate SQLException
```

## 2. 字段区

```java
private final ConnectionProvider connectionProvider;
```

连接入口。`DefaultRelationalTemplate` 不直接依赖 `DataSource`，而是通过 `ConnectionProvider` 拿 `ConnectionHandle`。

这样普通 JDBC、Spring 事务、未来多数据源，都可以通过不同 Provider 替换。

```java
private final SqlExceptionTranslator exceptionTranslator;
```

异常翻译器。它把 `SQLException` 转成稳定的 `RelationalAccessException`。

上层技术组件不应该直接判断 MySQL vendorCode 或 PostgreSQL SQLState，而是判断 `RelationalFailureType`。

```java
private final SqlExecutionListener executionListener;
```

旁路观测扩展点。后续慢 SQL、Micrometer、Trace 都可以走它。

注意：监听器异常不能影响 SQL 主流程，所以代码里所有 listener 调用都是 safeXXX。

```java
private final SqlStatementValidator validator;
private final JdbcStatementConfigurer statementConfigurer;
private final JdbcParameterBinder parameterBinder;
```

这三个是核心模板的辅助对象：

- `SqlStatementValidator`：校验 operationName、sql、options、参数结构；
- `JdbcStatementConfigurer`：设置 queryTimeout、fetchSize、maxRows；
- `JdbcParameterBinder`：把 `SqlParameter` 绑定到 `PreparedStatement`。

它们被拆出去，是为了让主模板只表达主流程。

## 3. 构造方法

```java
public DefaultRelationalTemplate(ConnectionProvider connectionProvider,
                                 SqlExceptionTranslator exceptionTranslator)
```

这是最小构造。没有传 listener 时使用 NOOP listener。

```java
public DefaultRelationalTemplate(ConnectionProvider connectionProvider,
                                 SqlExceptionTranslator exceptionTranslator,
                                 SqlExecutionListener executionListener)
```

这是完整构造。Starter 会把多个 `SqlExecutionListener` 合并成一个 composite listener 后传进来。

这里所有关键依赖都用 `Objects.requireNonNull`，因为核心模板没有这些对象无法工作。

## 4. queryOne

`queryOne` 的语义是：最多返回一行。

流程：

```text
1. validate(statement)
2. requireNonNull(rowMapper)
3. contextOf(statement, QUERY_ONE)
4. execute(context, connection -> {...})
5. prepareStatement(sql)
6. configure
7. bind
8. executeQuery
9. 没有数据 -> Optional.empty()
10. 一行数据 -> RowMapper.map
11. 第二行存在 -> NON_UNIQUE_RESULT
12. 返回 Optional.of(value)
```

为什么超过一行要报错？

因为 `queryOne` 表达的是“我逻辑上只接受一条”。如果数据库返回多条，说明 SQL 条件、唯一键、业务假设至少有一个不稳定。这里 fail-fast 比偷偷取第一条安全。

为什么 mapper 不允许返回 null？

因为 `Optional.empty()` 已经表达“数据库没行”。如果 mapper 返回 null，就会混淆“没查到”和“查到了但映射成 null”。所以统一转为 `RESULT_MAPPING_ERROR`。

## 5. queryList

`queryList` 的语义是：返回多行。

它和 `queryOne` 最大区别：

```text
while (resultSet.next()) {
    values.add(mapRow(context, rowMapper, resultSet));
}
```

返回时用 `List.copyOf(values)`，避免调用方拿到内部可变集合。

它仍然不做分页、不做流式游标、不解析 SQL。分页 SQL 应由上层 Storage 或业务 Mapper 决定。

## 6. queryScalar

`queryScalar` 的语义是：查单个值。

典型场景：

```sql
SELECT COUNT(*) FROM iron_outbox
SELECT status FROM iron_idempotency WHERE idempotency_key = ?
SELECT MAX(id) FROM xxx
```

它只取：

```java
resultSet.getObject(1, requiredType)
```

也就是第一列。

如果返回多行，仍然是 `NON_UNIQUE_RESULT`。

如果第一列是 SQL NULL，则返回 `Optional.empty()`。

## 7. update

`update` 是整个组件最重要的方法。

注意：这里的 update 不是只执行 SQL UPDATE，而是 JDBC 的 `executeUpdate()` 路径。

也就是说，下面这些 SQL 都走这个方法：

```text
INSERT INTO ...
UPDATE ...
DELETE FROM ...
MERGE INTO ...
INSERT ... ON DUPLICATE KEY UPDATE ...
```

真正执行什么，由 `SqlStatement.sql()` 决定。

Relational Access 不提供 `insertSelective`、`updateById`、`updateByUniqueKey`，因为这些需要表结构、列元数据、主键/唯一键语义，属于 MyBatis、JPA、业务 Repository 或具体 Storage Adapter。

`update` 只返回：

```java
new UpdateResult(preparedStatement.executeUpdate())
```

也就是 JDBC 受影响行数。

在幂等组件里，最常见用法是 CAS：

```text
affectedRows == 1 -> 抢占成功 / 状态变更成功
affectedRows == 0 -> 状态不满足 / 被别人抢走 / 已处理
```

## 8. batchUpdate / batch

`batchUpdate` 代表 JDBC batch 路径：

```text
prepareStatement(fixed sql)
for each parameter group:
    bind parameters
    addBatch
executeBatch
```

它同样不区分 batch insert / batch update / batch delete。

最终批量执行什么，由 `BatchSqlStatement.sql()` 决定。

`batch` 是 `batchUpdate` 的语义化别名，目的是提醒读代码的人：它不是只能批量更新，也可以批量插入或删除。

## 9. execute：真正的模板方法

`execute` 是这个类最核心的方法。

```java
long startedNanos = System.nanoTime();
```

记录开始时间，用于 listener 上报耗时。

```java
safeBefore(context);
```

执行前通知。即使 listener 抛异常，也不能阻断 SQL 主流程。

```java
try (ConnectionHandle handle = connectionProvider.acquire(context)) {
```

从 Provider 获取连接句柄。这里拿到的可能是：

```text
OWNED   -> 当前调用自己获取，结束后可以关闭
BORROWED -> 外部事务绑定，结束后不能物理关闭
```

```java
Connection connection = Objects.requireNonNull(handle.connection(), ...);
```

防御 Provider 实现错误，不能返回 null connection。

```java
result = work.execute(connection);
```

真正的 SQL 执行由传入的 lambda 完成，例如 queryOne/update/batch 各自不同。

```java
safeAfterSuccess(context, elapsedSince(startedNanos));
```

成功后通知 listener。

为什么成功通知放在 `ConnectionHandle` close 之后？

因为如果执行成功但释放连接失败，整体仍然不能算完全成功。现在代码会优先保留真实失败结果。

## 10. execute 的异常分支

### RelationalAccessException

```java
catch (RelationalAccessException exception)
```

这是组件内部已经明确分类过的异常，比如：

- `NON_UNIQUE_RESULT`
- `RESULT_MAPPING_ERROR`
- `DATA_SOURCE_ROUTING_ERROR`

这类异常不需要再翻译，直接上抛。

### SQLException

```java
catch (SQLException exception)
```

这是 JDBC 抛出的原始异常，需要走 `translateSafely`。

### RuntimeException

```java
catch (RuntimeException exception)
```

这是非 JDBC 异常，例如 Provider 实现异常、Unexpected NPE 等。

统一包成：

```text
RelationalAccessException(UNKNOWN)
```

避免上层面对各种散乱运行时异常。

## 11. translateSafely

这是本版本新增的防御点。

以前如果自定义 `SqlExceptionTranslator` 自己抛了 RuntimeException，可能覆盖原始 SQLException。

现在策略是：

```text
translator 正常返回异常 -> 使用它
translator 返回 null -> fallback UNKNOWN，并保留原 SQLException 为 cause
translator 自己抛 RuntimeException -> fallback UNKNOWN，原 SQLException 做 cause，translator 异常放 suppressed
```

所以不会吞异常。

原始 SQL 异常在：

```java
exception.getCause()
```

翻译器自身失败在：

```java
exception.getSuppressed()
```

这样排查问题时，两条线都在。

## 12. mapRow

`mapRow` 专门包装 RowMapper 异常。

原因是 RowMapper 既可能抛：

```text
SQLException
RuntimeException
业务转换异常
```

上层不应该直接看到这些散乱异常，所以统一转为：

```text
RelationalAccessException(RESULT_MAPPING_ERROR)
```

如果 RowMapper 已经主动抛 `RelationalAccessException`，则原样上抛。

## 13. contextOf

`contextOf(SqlStatement, kind)` 把 API 输入转成 SPI 上下文。

普通 SQL 没有额外属性：

```java
Map.of()
```

batch SQL 会携带：

```java
Map.of("batchSize", statement.batches().size())
```

这个属性是低基数基础设施属性，可以用于 metrics 或日志。

不要把 orderId、userId、idempotencyKey 这种高基数业务值放进 attributes。

## 14. safeBefore / safeAfterSuccess / safeAfterFailure

这三个方法的原则：

```text
观测是旁路，不是主链路
```

所以 listener 抛 RuntimeException 会被吞掉。

这不是吞 SQL 异常。SQL 异常仍然会在主流程中被包装和抛出。

这里吞的是观测系统自己的异常，例如：

- metrics registry 异常
- trace exporter 异常
- slow sql listener 自己 NPE

不能因为监控挂了导致业务 SQL 失败。

## 15. 为什么删除 insertAndReturnKey

`insertAndReturnKey` 的 JDBC 路径确实特殊，但它会把 API 引导到数据库自增主键方向。

而 Iron Components 的基础表更应该使用上层生成的业务 ID：

```text
idempotencyKey
messageId
taskId
ownerToken
```

所以当前阶段删除它更干净。

如果未来真的有强需求，可以在具体 Storage 内部直接使用 MyBatis/JdbcTemplate，或者在 Relational Access v2 里重新引入更清晰的 generated-key 扩展模型。

## 16. 一句话总结

`DefaultRelationalTemplate` 的价值不是提供很多 CRUD 方法，而是把 JDBC 最容易重复、最容易出错的生命周期统一起来：

```text
连接怎么拿
Statement 怎么配
参数怎么绑
结果怎么映射
异常怎么翻译
连接怎么释放
观测怎么挂
```

上层 Storage 只需要关心：

```text
这条 SQL 是什么
参数是什么
结果如何解释
```
