# 基础使用示例

下面的场景全部使用当前模型。API 接口使用 api.resolver 包，算法与映射实现使用 core 包。

## 1. 从分片值到路由输入

```java
ShardValue tenantValue = ShardValue.of(42L);
ShardKey tenant = new ShardKey("tenant_id", tenantValue);
ShardKey order = ShardKey.of("order_id", "8");

CompositeShardKey shardKey = CompositeShardKey.of(tenant, order);
RouteContext context = RouteContext.builder()
        .routeName("order-create")
        .logicalTable("business_order")
        .shardKey(shardKey)
        .attribute("traceId", "trace-1")
        .build();
```

tenantValue 保存 LONG 类型和值 42；tenant、order 分别表示一个字段；shardKey 按 tenant_id、order_id 的顺序组合字段。

routeName 表达场景，logicalTable 表达逻辑表族，traceId 是扩展信息。分片计算只读取 shardKey，其他字段不会影响哈希。

## 2. 完整的计算与映射

```java
StorageRouteResolver resolver = new DefaultStorageRouteResolver(
        new HashShardResolver(10, 10),
        new RouteMappingStrategyFactory("order-db-", "business_order", 2)
                .create(TableIndexMode.GLOBAL_TABLE_INDEX));

StorageRoute route = resolver.resolve(context);
```

StorageRouteResolver 的完整包名是 com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver。

上述固定示例的结果：

| 读取方式 | 结果 |
| --- | --- |
| route.context().routeName() | order-create |
| route.context().logicalTable() | business_order |
| route.context().shardKey().keys() | tenant_id=LONG(42)，order_id=STRING("8") |
| route.context().attribute("traceId") | trace-1 |
| route.shardInfo().shardId() | 42 |
| route.shardInfo().databaseIndex() | 4 |
| route.shardInfo().localTableIndex() | 2 |
| route.location().dataSourceKey() | order-db-04 |
| route.location().tableName() | business_order_42 |

若改成 LOCAL_TABLE_INDEX，分片结果不变，物理表名变为 business_order_02。

route.context() 就是传入的 context。结果不把其中字段复制成另一套输入状态。

## 3. 单字段也使用 CompositeShardKey

```java
RouteContext single = RouteContext.builder()
        .routeName("order-create")
        .logicalTable("business_order")
        .shardKey(CompositeShardKey.of(ShardKey.of("order_id", "8")))
        .build();

StorageRoute singleRoute = resolver.resolve(single);
```

在同一配置下，单字段 "8" 的历史落点仍是 shardId=56、order-db-05.business_order_56。

单字段哈希保留历史值文本规则；复合字段使用包含字段名、类型和顺序的稳定编码。不要把一个已存在的单字段路由临时增加字段并直接访问历史数据。

## 4. 同分片的订单与 Outbox

```java
StorageRouteResolver outboxResolver = new DefaultStorageRouteResolver(
        new HashShardResolver(10, 10),
        new RouteMappingStrategyFactory("order-db-", "iron_outbox", 2)
                .create(TableIndexMode.GLOBAL_TABLE_INDEX));

RouteContext outboxContext = RouteContext.builder()
        .routeName("outbox-save")
        .logicalTable("iron_outbox")
        .shardKey(context.requireShardKey())
        .attribute("traceId", context.attribute("traceId"))
        .build();

StorageRoute outboxRoute = outboxResolver.resolve(outboxContext);
```

订单与 Outbox 使用相同分片键、算法、库表数量，所以分片结果相同，但物理表分别是 business_order_42 和 iron_outbox_42。

当前表前缀由映射策略配置，并不会读取 logicalTable 自动生成任意表名。上例分别配置了两个策略；路由结果也不自动保证同一事务，还需要后续存储层复用正确的资源与事务连接。

## 5. RouteContext 与线程上下文

```java
StorageRouteContext routeContextStore = new ThreadLocalStorageRouteContext();

try (StorageRouteScope ignored = routeContextStore.open(route)) {
    StorageRoute current = routeContextStore.requireCurrent();
    RouteContext input = current.context();
    CompositeShardKey key = input.requireShardKey();
    // 当前已实现：线程内读取完整路由，复合键不会丢失。
    // 后续接入：各 Repository / Storage 读取分片信息并映射自己的表。
}
```

RouteContext 是输入模型。StorageRouteContext 是保存和读取当前路由的接口；ThreadLocalStorageRouteContext 是其实现。关闭最外层作用域后清理线程状态，嵌套作用域关闭后恢复外层路由。

## 6. StorageRoute 到 Relational Access

直连多数据源场景使用 `StorageRouteToSqlRouteBridge`：

```java
StorageRouteToSqlRouteBridge bridge = new DefaultStorageRouteToSqlRouteBridge();

String tableName = bridge.requireTableName(singleRoute);
SqlStatement statement = SqlStatement.of("idempotency.insert",
        "INSERT INTO " + tableName + "(idempotency_key, order_id, status) VALUES (?, ?, ?)",
        key, orderId, "SUCCESS");

relationalTemplate.update(bridge.applyRoute(statement, singleRoute));
```

bridge 只做两件事：

| 数据 | 用途 |
| --- | --- |
| `bridge.toSqlRoute(route)` | 交给 Relational Access 选择 DataSource |
| `bridge.requireTableName(route)` | 交给具体 Storage 拼装已经确定的最终 SQL |

`RelationalTemplate` 不理解分片键，也不改写表名。每个 Storage 仍要基于自己的逻辑表族解析出对应的 `StorageRoute`，
不能把业务订单表的 `tableName` 直接拿来写幂等表或 Outbox 表。

当前默认 bridge 只支持 `DIRECT_DATASOURCE`。`SHARDINGSPHERE_JDBC` 和 `PROXY` 需要专门 adapter 决定如何接入中间件。

## 7. 固定直连

固定直连不需要为了凑模型而伪造分片键：

```java
StorageRoute fixed = StorageRoute.direct("business_order", "order-db", "business_order");
// fixed.context().logicalTable() == "business_order"
// fixed.context().shardKey() == null
// fixed.shardInfo() == null
```

单字段与复合键都通过 route.context().shardKey().keys() 读取；StorageRoute 不提供单字段专用入口，避免丢失组合条件。

## 8. 库表数量

databaseCount 是库数，tablesPerDatabase 是每库表数，总分片数是两者乘积。

- 10 库、每库 10 表：共 100 个分片。
- 10 库、每库 100 表：共 1000 个分片。
- GLOBAL_TABLE_INDEX 按全局 shardId 命名表。
- LOCAL_TABLE_INDEX 按库内 localTableIndex 命名表。
- dataSourceIndexWidth 控制库号补零宽度，tableIndexWidth 控制表号补零宽度。

`DIRECT_DATASOURCE` 不决定是哪一种表编号，它只表示最终结果已经解析出了 `dataSourceKey` 和 `tableName`。
直连场景下，常见配置如下：

| 拓扑 | 配置 | 表后缀含义 | 示例 |
| --- | --- | --- | --- |
| 10 库，每库 10 表，全局表号 | `databaseCount=10, tablesPerDatabase=10, tableIndexMode=GLOBAL_TABLE_INDEX` | `db_00` 是 `order_00` 到 `order_09`，`db_01` 是 `order_10` 到 `order_19` | `db_09.order_99` |
| 10 库，每库 10 表，库内重复表号 | `databaseCount=10, tablesPerDatabase=10, tableIndexMode=LOCAL_TABLE_INDEX` | 每个库内都是 `order_00` 到 `order_09` | `db_09.order_09` |
| 10 库，每库 100 表，库内重复表号 | `databaseCount=10, tablesPerDatabase=100, tableIndexMode=LOCAL_TABLE_INDEX` | 每个库内都是 `order_00` 到 `order_99` | `db_09.order_99` |

默认 `dataSourceIndexWidth=2`、`tableIndexWidth=2`，所以库号和表号都是两位。若未来表后缀需要 `000` 到 `999`，
只调大 `tableIndexWidth` 即可，不必改变库号宽度。

本轮不包含 SQL 解析/改写、扩容迁移、分布式事务或中间件适配。字段顺序、类型与稳定编码见 [StorageRoute 模型](03-storage-route-model.md)。
