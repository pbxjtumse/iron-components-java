# 02. Basic Usage Examples

> 目标：用订单场景解释 StorageRouteRequest / StorageRoute / StorageRouteContext / Resolver 到底怎么用。

## 1. 核心概念一句话

```text
StorageRouteRequest
    调用方告诉路由组件：我要处理哪个逻辑表、用哪个分片键、当前是什么业务场景。

StorageRouteResolver
    路由组件根据 request 算出：本次应该去哪个 dataSourceKey、哪张 tableName。

StorageRoute
    路由结果：本次存储访问应该去哪。

StorageRouteContext
    把这份 route 绑定到当前线程，让业务 Repository、幂等、Outbox、任务等拿到同一份路由。
```

## 2. scene 是什么

`scene` 表示“当前为什么要路由”，不是库名，也不是表名。

常见例子：

```text
order-create
order-pay
idempotency-try-acquire
idempotency-mark-success
outbox-save
task-recover
```

它主要用于：

```text
日志
指标
告警
规则区分
排查问题
```

例如同样是订单表，创建订单和支付订单可能都用 `business_order` 这张逻辑表，但场景不同：

```java
StorageRouteRequest createOrder = StorageRouteRequest.builder()
        .scene("order-create")
        .logicalTable("business_order")
        .shardKeyName("order_id")
        .shardKeyValue("order-10001")
        .build();

StorageRouteRequest payOrder = StorageRouteRequest.builder()
        .scene("order-pay")
        .logicalTable("business_order")
        .shardKeyName("order_id")
        .shardKeyValue("order-10001")
        .build();
```

## 3. logicalTable 是什么

`logicalTable` 表示“业务上我要访问哪类表”。

它不是一定带后缀的物理表。

例如：

```text
logicalTable = business_order
physical table = business_order_017
```

对于技术组件也是一样：

```text
logicalTable = iron_idempotency_record
physical table = iron_idempotency_record_017
```

为什么要保留 logicalTable？

因为业务表、幂等表、Outbox 表可能都是同一个分片键，但表前缀不同：

```text
order_id = order-10001

business_order           -> business_order_017
iron_idempotency_record  -> iron_idempotency_record_017
iron_outbox              -> iron_outbox_017
```

它们应该落到同一个库，但可以是不同的物理表。

## 4. 一个完整订单例子

```java
ShardIdHashStorageRouteResolver resolver = ShardIdHashStorageRouteResolver.builder()
        .dataSourcePrefix("order-db-")
        .databaseCount(10)
        .tablePrefix("business_order")
        .tablesPerDatabase(10)
        .tableIndexMode(TableIndexMode.GLOBAL_TABLE_INDEX)
        .build();

StorageRouteRequest request = StorageRouteRequest.builder()
        .scene("order-create")
        .logicalTable("business_order")
        .shardKeyName("order_id")
        .shardKeyValue("8")
        .attribute("idempotencyTable", "iron_idempotency_record")
        .attribute("outboxTable", "iron_outbox")
        .build();

StorageRoute route = resolver.resolve(request);
```

得到的 route 是：

```text
mode           = DIRECT_DATASOURCE
routeName      = order-create
logicalTable   = business_order
shardInfo      = {shardId=56, databaseIndex=5, localTableIndex=6, totalShardCount=100}
physicalLocation = {dataSourceKey=order-db-05, tableName=business_order_56}
shardKeyName   = order_id
shardKeyValue  = 8
attributes     = {idempotencyTable=iron_idempotency_record, outboxTable=iron_outbox}
```

`idempotencyTable` 和 `outboxTable` 在这里仅是透传的扩展信息，当前解析器不会据此自动映射其他表。

## 5. 绑定到当前调用链

```java
StorageRouteContext context = new ThreadLocalStorageRouteContext();

try (StorageRouteScope ignored = context.open(route)) {
    StorageRoute current = context.requireCurrent();
    // 当前已实现：在本线程读取同一份路由。
    // 后续接入：各 Repository / Storage 读取分片信息，使用各自的表映射并执行 SQL。
}
```

业务 Repository、幂等、Outbox 后续可以从上下文读取相同分片依据，但需要各自的物理表。
例如同一个 `ShardRouteInfo(56, 5, 6, 100)` 可分别映射到 `order-db-05.business_order_56`、
`order-db-05.iron_idempotency_record_56` 和 `order-db-05.iron_outbox_56`。
上下文目前只传播数据，未接入上述 Storage，也不会自动创建事务。

## 6. 10 库 100 表如何理解

当前 `databaseCount` 表示库数，`tablesPerDatabase` 表示每库的表数量，总分片数是两者乘积。

例如：

```text
databaseCount     = 10
tablesPerDatabase = 100
```

表示：

```text
10 个库，每个库 100 张表
LOCAL_TABLE_INDEX：每库 business_order_00 到 business_order_99
GLOBAL_TABLE_INDEX：第 0 库 _00 到 _99，第 1 库 _100 到 _199，以此类推
总物理表数量 = 10 * 100
```

若你的业务说“总共 100 张物理表，10 个库，每个库 10 张表”，则应该理解成：

```text
databaseCount     = 10
tablesPerDatabase = 10
```

全局 100 表分布到 10 库已经由 `GLOBAL_TABLE_INDEX` 支持。
如需复用同一分片算法、替换命名策略，可显式组合 `HashShardRouteResolver`、`RouteMappingStrategyFactory` 和
`DefaultStorageRouteResolver`，见 [StorageRoute 模型](03-storage-route-model.md)。

## 7. 当前第一版不要误用

第一版适合：

```text
理解模型
验证上下文传播
给 Idempotency / Outbox / Task 后续接入打基础
```

第一版不适合直接承担完整生产分库分表：

```text
不支持扩容迁移
不支持复杂分片算法
不支持 SQL 解析改写
不支持跨库结果归并
不支持分布式事务
```
