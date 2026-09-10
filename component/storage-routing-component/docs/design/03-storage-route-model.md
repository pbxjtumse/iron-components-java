# StorageRoute 模型调整

本轮完成结构化路由结果的统一：请求信息、逻辑分片和物理位置分别表达；哈希路由与默认编排器使用同一条执行链路。

## 1. 字段职责

| 字段 | 表达什么 | 示例 |
| --- | --- | --- |
| `mode` | 如何解释路由结果 | `DIRECT_DATASOURCE` |
| `routeName` | 请求中的业务场景 `scene` | `order-create` |
| `logicalTable` | 请求要访问的逻辑表 | `business_order` |
| `shardInfo` | 分片计算结果 | `shardId=56, databaseIndex=5, localTableIndex=6, totalShardCount=100` |
| `physicalLocation` | 已解析出的物理数据源和表 | `db_05.business_order_56` |
| `shardKeyName / shardKeyValue` | 本次路由使用的分片键 | `order_id=8` |
| `attributes` | 业务扩展信息 | `tenantId, traceId` |

`dataSourceKey()`、`tableName()` 是 `physicalLocation` 的便捷读取方法，不另存一份库表状态。
`logicalTable()` 不从物理表后缀推断，`tableName()` 也不会在物理位置未知时返回逻辑表。

`StorageRoute` 对扩展 Map 做结构快照并返回只读 Map；其中的对象值不会被深复制，调用方应保证其稳定性。

## 2. 当前解析链路

```java
StorageRouteResolver resolver = new DefaultStorageRouteResolver(
        new HashShardRouteResolver(10, 10),
        new RouteMappingStrategyFactory("db_", "business_order", 2)
                .create(TableIndexMode.GLOBAL_TABLE_INDEX));

StorageRoute route = resolver.resolve(StorageRouteRequest.builder()
        .scene("order-create")
        .logicalTable("business_order")
        .shardKeyName("order_id")
        .shardKeyValue("8")
        .attribute("tenantId", "tenant-1")
        .build());
```

这里 `StorageRouteResolver` 指 `com.xjtu.iron.storage.routing.api.resolver.StorageRouteResolver`。

结果为：逻辑表 `business_order`，分片 `56`，数据库下标 `5`，库内表下标 `6`，物理位置 `db_05.business_order_56`。

1. `HashShardRouteResolver` 使用分片键计算编号，不知道库表前缀。
2. `RouteMappingStrategy` 将编号映射为 `PhysicalStorageLocation`。
3. `DefaultStorageRouteResolver` 组装 `StorageRoute`，保留场景、逻辑表、分片键和请求扩展属性。

`ShardIdHashStorageRouteResolver.builder()` 保留为便捷入口，内部组合以上对象，默认编号宽度仍为 2。
原有的 `String.valueOf(key).hashCode()` 与 `Math.floorMod` 算法保持不变。分片键必须有稳定的字符串表示；
调整算法、分片总数或键表示仍需单独规划迁移。

## 3. 全局表编号与库内表编号

在 10 库、每库 10 表的配置中，同一个 `ShardRouteInfo(56, 5, 6, 100)` 有两种命名方式：

| 映射策略 | 数据源 | 表名 |
| --- | --- | --- |
| `GLOBAL_TABLE_INDEX` | `db_05` | `business_order_56` |
| `LOCAL_TABLE_INDEX` | `db_05` | `business_order_06` |

映射策略按构造时的表前缀工作，尚未提供按 `request.logicalTable()` 自动选择规则的注册表。
订单、幂等、Outbox 必须分别配置对应的表映射策略；它们可以复用同一份 `ShardRouteInfo`。

同分片不等于同表，也不自动等于同事务。后续集成仍要确保各 SQL 复用事务管理器管理的同一资源和连接。
当前上下文不会切换数据源、生成 SQL 或打开事务。

## 4. 构造约束

- `PhysicalStorageLocation` 必须同时包含非空数据源 key 和物理表名，字段会去除首尾空格。
- `DIRECT_DATASOURCE` 路由必须有完整物理位置；固定直连可以没有分片编号。
- 两参数 `StorageRoute.direct(dataSourceKey, tableName)` 保留，逻辑表为空；新增三参数形式显式提供逻辑表。
- 旧 Builder 的 `.dataSourceKey(...).tableName(...)` 支持任意设置顺序，在 `build()` 时统一校验。
- `SHARDINGSPHERE_JDBC`、`PROXY` 仍只是预留模式，可表达物理位置未知；尚无中间件适配器，不能据此宣称已经接入。
- 分片数量必须为正，库数与每库表数乘积不得超过 `int` 范围；分片坐标不得为负或超过总分片范围。
- 编排器对缺失的分片结果、物理映射结果立即报错，避免把不可执行的直连路由传给下游。

物理名称的数字格式使用 `Locale.ROOT`，不会随 JVM 默认语言环境改变为本地化数字。

## 5. 兼容与迁移

| 旧用法 | 本轮处理 / 新用法 |
| --- | --- |
| `api.StorageRouteResolver` | 保留为弃用兼容别名，继承 `api.resolver.StorageRouteResolver`；所有内置解析器均兼容两者 |
| `StorageRoute.dataSourceKey()` / `tableName()` | 保留，从 `physicalLocation` 读取 |
| `.dataSourceKey(...).tableName(...)` | 保留，在构造最终模型时校验 |
| `route.attribute("shardId")` | 改用 `route.shardInfo().shardId()` |
| `route.attribute("databaseIndex")` | 改用 `route.shardInfo().databaseIndex()` |
| `route.attribute("localTableIndex")` | 改用 `route.shardInfo().localTableIndex()` |
| `route.attribute("totalShardCount")` | 改用 `route.shardInfo().totalShardCount()` |
| `route.attribute("physicalTableIndex")` / `tableIndexMode` | 不再由解析器注入；执行时直接使用物理表名，策略模式在配置中指定 |
| `route.attribute("logicalTable")` | 改用 `route.logicalTable()` |
| 默认 Builder 空路由、仅库或仅表的直连路由 | 构造时拒绝；调用方需补全库表 |

固定直连路由的 `shardInfo()` 可以为空，读取前应区分是否执行了分片计算。请求扩展属性会原样保留，
即使调用方放入同名属性，也不能用它覆盖专用模型字段。此前文档里的 `HashStorageRouteResolver` 已不存在，
应使用 `ShardIdHashStorageRouteResolver` 或显式组合 `HashShardRouteResolver` 与默认编排器。

## 6. 验证

在仓库根目录运行：

```bash
mvn -pl :storage-routing-core -am test
```

回归覆盖逻辑/物理表区分、旧 Builder 顺序、Map 快照、两套接口兼容、两种表编号、负哈希值、
同分片不同表、配置溢出、空解析结果、语言环境与已有 ThreadLocal 上下文行为。

相关图示：[模型关系](../component/01-storage-route-model.puml)、[解析时序](../sequence/03-storage-route-resolution.puml)。
