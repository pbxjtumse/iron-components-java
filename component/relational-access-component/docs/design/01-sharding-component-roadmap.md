# Sharding Component Roadmap

> 目标：在不重新造 ShardingSphere 的前提下，为 Iron Components 提供统一路由上下文，让业务数据、幂等记录、Outbox、Task 等技术组件记录可以落到同一分片，并在同一本地事务内提交或回滚。

## 1. 为什么需要这层

Relational Access 只解决：

```text
SqlRoute.dataSourceKey -> DataSource
SqlStatement.sql       -> PreparedStatement
```

它不认识业务 `orderId / userId / merchantId`，也不应该计算分片。

但是 Idempotency / Outbox / Task 这类组件有一个共同问题：它们的技术记录经常需要和业务数据同库同事务。

例如：

```text
business_order
iron_idempotency_record
iron_outbox
iron_task
```

如果业务表在 `order-db-1`，幂等表却在 `infra-db`，那就不是一个普通本地事务了。

因此需要一个比 Relational Access 更上层的路由抽象。

## 2. 当前已打穿的底座

Relational Access 当前已经具备：

```text
SqlRoute
DataSourceResolver
RoutingDataSourceResolver
SpringTransactionAwareConnectionProvider
```

它能够证明：

```text
业务侧 JdbcTemplate/MyBatis 使用目标 DataSource
技术组件 RelationalTemplate 使用同一个 dataSourceKey
两者可以共享 Spring transaction-bound Connection
```

对应验证测试：

```text
SameShardSharedTransactionIntegrationTest
```

这个测试用两个 H2 DataSource 模拟两个业务分片库，验证：

```text
同 shard：业务表 + 幂等表一起提交、一起回滚
错 shard：业务库回滚不代表另一个库的幂等记录也能回滚
```

## 3. Sharding Component 的定位

Sharding Component 不做这些事情：

- SQL 解析
- SQL 改写
- 跨库查询结果归并
- 自动扩缩容
- 分布式事务
- 替代 Apache ShardingSphere

它只做：

```text
shardKey -> StorageRoute
StorageRoute -> 被业务 Repository / 技术组件 Storage 共同使用
```

也就是统一回答：

```text
这次业务请求对应哪个逻辑库？
这次技术组件记录应该写哪张逻辑表或物理表？
这次 SQL 是否必须携带 shardKey？
当前是直连多库模式，还是 ShardingSphere-JDBC 模式？
```

## 4. 已落地的核心模型

当前已经建立独立 `storage-routing-component`，提供 `storage-routing-api` 与 `storage-routing-core`。
以下为字段摘要，完整约束见 [StorageRoute 模型](../../../storage-routing-component/docs/design/03-storage-route-model.md)。

### StorageRoute

```java
public final class StorageRoute {

    /**
     * 路由模式。
     * DIRECT_DATASOURCE：应用内显式选择 DataSource。
     * SHARDINGSPHERE_JDBC：统一走 ShardingSphereDataSource。
     */
    private final StorageRouteMode mode;

    /** 输入上下文：场景、逻辑表、类型化分片键和扩展属性只在这里保存。 */
    private final RouteContext context;

    /** 分片计算结果；固定直连可以为空。 */
    private final ShardRouteInfo shardInfo;

    /** 已解析的物理库表；DIRECT_DATASOURCE 模式必填。 */
    private final PhysicalStorageLocation location;
}
```

### StorageRouteMode

```java
public enum StorageRouteMode {
    DIRECT_DATASOURCE,
    SHARDINGSPHERE_JDBC,
    PROXY
}
```

### StorageRouteResolver

新代码使用 `api.resolver.StorageRouteResolver`。旧的 `api.StorageRouteResolver` 保留为兼容别名。

```java
public interface StorageRouteResolver {
    StorageRoute resolve(RouteContext context);
}
```

直连解析链路已经实现：`RouteContext` 的 `CompositeShardKey` → `HashShardResolver` → `ShardRouteInfo` →
`RouteMappingStrategy` → `PhysicalStorageLocation` → 组合式 `StorageRoute`。
`ShardResolver` 只接收类型化分片键，单字段同样使用单元素 `CompositeShardKey`。
`storage-routing-integration-relational` 已提供 `StorageRouteToSqlRouteBridge`，可以把 `DIRECT_DATASOURCE`
路由交给 Relational Access 选择 DataSource，并把物理表名交给具体 Storage 拼最终 SQL。
中间件适配和技术组件 Storage 接入仍属于后续工作。
`DIRECT_DATASOURCE` 表示已经解析出物理库表，不表示固定某一种表编号；10 库每库 10 表和 10 库每库 100 表
都由 `databaseCount`、`tablesPerDatabase` 与 `TableIndexMode` 组合表达。

## 5. 直连多库模式

适合当前 `RoutingDataSourceResolver`。

```text
业务 shardKey = orderId
    -> dataSourceKey = order-db-1
    -> businessTable = business_order_001
    -> idempotencyTable = iron_idempotency_record_001
```

业务 MyBatis 使用同一个 `dataSourceKey`，幂等组件通过：

```java
SqlStatement.of(...).withRoute(SqlRoute.of(storageRoute.dataSourceKey()))
```

访问同一个库。

表名由 Storage 拼入最终 SQL：

```sql
INSERT INTO iron_idempotency_record_001(...)
```

## 6. ShardingSphere-JDBC 优先模式

这是后续第一优先级适配方向。

```text
MyBatis
RelationalTemplate
    -> same ShardingSphereDataSource
        -> ShardingSphere route/rewrite/execute
```

这时 Iron Relational Access 不需要知道真实 `order-db-1`。

关键要求是：

```text
业务 SQL 和技术组件 SQL 都必须携带同一个 shardKey
```

例如业务 SQL：

```sql
INSERT INTO business_order(order_id, amount) VALUES (?, ?)
```

幂等 SQL：

```sql
INSERT INTO iron_idempotency_record(idempotency_key, order_id, status)
VALUES (?, ?, ?)
```

如果幂等 SQL 不带 `order_id`，ShardingSphere 可能无法精准路由。

## 7. 与 Idempotency 的关系

Idempotency 不应该直接依赖 ShardingSphere。

它应该依赖路由结果：

```text
IdempotencyExecutor
    -> resolve StorageRoute
    -> Tx-A tryAcquire(route)
    -> Tx-B business callback + markSuccess(route)
    -> Tx-C markFailed(route)
```

Tx-A / Tx-B / Tx-C 必须使用同一个 route。

特别是 Tx-B：

```text
业务 MyBatis 写业务表
JdbcIdempotencyStorage 通过 RelationalTemplate 写幂等表
```

必须同库同事务。

## 8. 推进顺序

```text
1. Relational Access 打穿同库同事务测试
2. 抽 sharding-component 的 StorageRoute / StorageRouteResolver
3. 打通 storage-routing-integration-relational 与 storage-routing-starter
4. Idempotency JDBC Storage 接 StorageRoute
5. ShardingSphere-JDBC 模式下验证：MyBatis + RelationalTemplate 共享 ShardingSphereDataSource
6. 再考虑 ShardingSphere-Proxy、MyCAT、TiDB 等扩展
```

## 9. 当前结论

Sharding Component 是必要的，但它不是数据库中间件。

它是 Iron Components 的“路由上下文标准”。

真正的 SQL 分片执行优先站在 Apache ShardingSphere-JDBC 之上。
当前 direct bridge 是为了让 Iron 技术组件先共用同一份 StorageRoute，不替代后续 ShardingSphere-JDBC adapter。
