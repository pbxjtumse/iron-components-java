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
HashStorageRouteResolver resolver = HashStorageRouteResolver.builder()
        .dataSourcePrefix("order-db-")
        .dataSourceCount(10)
        .tablePrefix("business_order")
        .tableCount(100)
        .dataSourceIndexWidth(1)
        .tableIndexWidth(3)
        .build();

StorageRouteRequest request = StorageRouteRequest.builder()
        .scene("order-create")
        .logicalTable("business_order")
        .shardKeyName("order_id")
        .shardKeyValue("order-10001")
        .attribute("idempotencyTable", "iron_idempotency_record")
        .attribute("outboxTable", "iron_outbox")
        .build();

StorageRoute route = resolver.resolve(request);
```

得到的 route 大概是：

```text
mode           = DIRECT_DATASOURCE
routeName      = order-create
dataSourceKey  = order-db-某个下标
tableName      = business_order_某个下标
shardKeyName   = order_id
shardKeyValue  = order-10001
attributes     = {logicalTable, dataSourceIndex, tableIndex, ...}
```

## 5. 绑定到当前调用链

```java
StorageRouteContext context = new ThreadLocalStorageRouteContext();

try (StorageRouteScope ignored = context.open(route)) {
    orderRepository.save(order);
    idempotencyStorage.markSuccess(idempotencyKey);
    outboxStorage.save(event);
}
```

这段代码的核心不是 ThreadLocal 本身，而是：

```text
orderRepository
idempotencyStorage
outboxStorage
```

都可以拿到同一份 route，避免业务数据和技术组件记录落到不同库。

## 6. 10 库 100 表如何理解

当前 HashStorageRouteResolver 的 `tableCount` 表示“每个 DataSource 里的表数量”。

例如：

```text
dataSourceCount = 10
tableCount      = 100
```

表示：

```text
10 个库
每个库 100 张 business_order_000 到 business_order_099
总物理表数量 = 10 * 100
```

若你的业务说“总共 100 张物理表，10 个库，每个库 10 张表”，则应该理解成：

```text
dataSourceCount = 10
tableCount      = 10
```

后续如果需要更复杂的规则，例如全局 100 表均匀映射到 10 库，应该抽 RouteAlgorithm / TableMapping 规则，而不是继续把 HashStorageRouteResolver 变复杂。

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
