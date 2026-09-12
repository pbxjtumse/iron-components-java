# StorageRoute 模型与类型化分片键

本轮按原设计统一输入模型：单字段与复合字段都使用 CompositeShardKey，RouteContext 承载输入，StorageRoute 组合上下文、分片结果和物理位置。

## 1. 模型职责

| 模型 | 职责 |
| --- | --- |
| ShardValue | 稳定值及明确类型，例如 LONG(42)、STRING("8") |
| ShardKey | 字段名 + ShardValue，例如 tenant_id + LONG(42) |
| CompositeShardKey | 非空、有序、字段名不重复的分片字段集合 |
| RouteContext | routeName、logicalTable、CompositeShardKey、attributes |
| ShardRouteInfo | shardId、databaseIndex、localTableIndex、totalShardCount |
| PhysicalStorageLocation | 完整物理 dataSourceKey 和 tableName |
| StorageRoute | context、shardInfo、location，保留原有 mode 描述字段 |

StorageRoute 的核心结构：

```java
public final class StorageRoute {
    private final StorageRouteMode mode;
    private final RouteContext context;
    private final ShardRouteInfo shardInfo;
    private final PhysicalStorageLocation location;
}
```

routeName、logicalTable、分片键、attributes 只保存在 RouteContext。旧便捷 getter 从该对象读取，不再保存第二份平铺字段。

RouteContext 是输入数据；StorageRouteContext 是具有 current/open 方法的上下文访问接口，ThreadLocalStorageRouteContext 负责在线程内传播结果。这三个名称的职责不同。

## 2. API 与 Core 边界

| 所在模块 | 类或接口 |
| --- | --- |
| storage-routing-api | 上述七个模型；resolver.StorageRouteResolver、resolver.ShardResolver、mapping.RouteMappingStrategy 等契约 |
| storage-routing-core | DefaultStorageRouteResolver、HashShardResolver、Global/Local 映射策略、映射工厂、固定路由与 ThreadLocal 实现 |
| storage-routing-integration-relational | StorageRouteToSqlRouteBridge、DefaultStorageRouteToSqlRouteBridge |
| storage-routing-starter | StorageRoutingAutoConfiguration、StorageRoutingProperties |

主契约分别是：

```java
StorageRoute resolve(RouteContext context);           // StorageRouteResolver
ShardRouteInfo resolve(CompositeShardKey shardKey);    // ShardResolver
PhysicalStorageLocation map(ShardRouteInfo shardInfo); // RouteMappingStrategy
```

DefaultStorageRouteResolver 从 context.requireShardKey() 取得键，执行计算、映射，再原样保存输入 context 并组装结果。ShardResolver 看不到场景、逻辑表和扩展属性，因此不会因调用方的日志元数据改变分片。

当前映射策略按配置的表前缀工作，不自动按 logicalTable 选择规则。订单、幂等、Outbox 必须分别配置自己的映射策略，可以共享同一份分片键与计算结果。

## 3. ShardValue 的支持类型

| Java 精确类型 | 类型标记 | 文本规则 |
| --- | --- | --- |
| String | STRING | 原样保留，包括空串与首尾空格 |
| Byte / Short | BYTE / SHORT | 十进制文本 |
| Integer / Long | INTEGER / LONG | 十进制文本 |
| BigInteger | BIG_INTEGER | 十进制文本 |
| BigDecimal | DECIMAL | toString，保留 scale，不做数值归一化 |
| UUID | UUID | UUID 标准文本 |

字符串 "8"、整数 8、长整数 8L 在模型中是不同值。BigDecimal("1.0") 与 BigDecimal("1.00") 也是不同值。

null、数组、集合、任意业务对象、浮点数、可变 Number 及上述类型的自定义子类会被拒绝。不要把任意对象的 toString 当成稳定分片协议；如需日期等其他类型，先由业务明确稳定格式并传入字符串。

扩展 attributes 仅对 Map 结构做快照，值不会深复制；分片字段则全部由不可变模型组成。

## 4. 单字段与复合字段的哈希规则

输入始终为 CompositeShardKey。单字段与多字段采用明确记录的两种哈希输入规则：

| 字段数 | HashShardResolver 的哈希输入 |
| --- | --- |
| 1 | 该字段值的稳定文本；保持受支持类型在旧版 String.valueOf(value).hashCode() 下的落点 |
| 大于 1 | CompositeShardKey.canonicalForm() 的 v1 编码，包含字段顺序、名称、类型和值 |

两者均对输入文本使用 Java String.hashCode，然后用 Math.floorMod(hash, totalShardCount) 得到 shardId。

单字段兼容规则意味着字段名、类型标记不参与单字段哈希。例如 STRING("8") 与 LONG(8) 都仍落到原来的分片，这不影响模型保留类型信息。不能将“模型值不同”误解为“必须落在不同分片”。

复合键 v1 编码：

- 前缀是版本和字段数，例如 v1;2;。
- 每个字段依次编码名称、类型名、值文本。
- 每段使用“UTF-16 code unit 长度:文本”；长度与 Java String.length 一致。
- 字段顺序由调用方的规则显式指定，不自动排序，也不读取任意 Map 的遍历顺序。
- 字段名去除首尾空格、区分大小写，重复名称拒绝构造。

例如 tenant_id=LONG(42)、order_id=STRING("8")：

```text
v1;2;9:tenant_id4:LONG2:428:order_id6:STRING1:8
```

长度前缀避免 ab+c 与 a+bc 的拼接歧义，也允许值本身包含冒号、分号、空串与 Unicode。它不保证分片哈希无碰撞；有限分片中多个键落在同一分片是正常行为。

在 10 库、每库 10 表时，上述复合键得到 shardId=42、databaseIndex=4、localTableIndex=2：
GLOBAL_TABLE_INDEX 对应 db_04.order_42，LOCAL_TABLE_INDEX 对应 db_04.order_02。

DIRECT_DATASOURCE 不是一种表编号算法，它只要求 StorageRoute 中已经有完整 PhysicalStorageLocation。
直连 10 库每库 10 表和 10 库每库 100 表都可以用同一个模式表达：databaseCount 固定为 10，
tablesPerDatabase 分别为 10 或 100，LOCAL_TABLE_INDEX 分别得到每库 order_00 ~ order_09 或
order_00 ~ order_99。若希望表名使用跨库全局编号，则使用 GLOBAL_TABLE_INDEX。
库号和表号的补零宽度分别由 dataSourceIndexWidth 与 tableIndexWidth 控制。

字段数量、字段顺序、类型、值格式、编码版本、哈希算法、库表数量都是路由规则。改变规则前必须规划已有数据如何迁移。模型对象自身的 hashCode 用于 JVM 集合，不能替代这里明确的路由哈希协议。

## 5. 构造与执行约束

- CompositeShardKey 必须非空，不允许 null 元素或重复字段名；单字段也用单元素集合表达。
- RouteContext 可不带键，以容纳固定直连元数据；需要计算分片的解析器在计算前拒绝缺失的键。
- DIRECT_DATASOURCE 必须有完整物理位置；固定直连可没有 shardInfo。
- location 与 physicalLocation() 是同一份状态；物理位置未知时，dataSourceKey()/tableName() 返回 null，不以逻辑表替代。
- 新 Builder 的 context(...) 不能和旧 routeName/logicalTable/shardKeyName/shardKeyValue/attributes 字段设置混用，防止两份输入互相覆盖。
- 扩展 attributes 不控制标准分片字段；即使存在同名属性，也不会覆盖显式模型。
- 分片数量、索引范围、乘法溢出、空解析结果等仍在边界校验；物理编号使用 Locale.ROOT。
- 默认 relational bridge 只接受 DIRECT_DATASOURCE，并要求完整物理位置。
- SHARDINGSPHERE_JDBC / PROXY 仅保留描述字段，当前没有实现中间件 adapter 或分布式事务。

同分片不等于同表，也不自动等于同事务。后续仍要让各 Storage 映射自己的表，并让 SQL 复用事务管理器管理的同一资源和连接。

## 6. 兼容与迁移

| 旧用法 | 处理方式 |
| --- | --- |
| StorageRouteRequest.builder()/of(...) | 保留为弃用适配器，内部只有一份 RouteContext；构造时转成单元素 CompositeShardKey |
| resolver.resolve(StorageRouteRequest) | 保留默认方法，转交 resolve(RouteContext) |
| HashShardRouteResolver | 保留为弃用薄适配器，委托 HashShardResolver |
| ShardRouteResolver | 保留旧名称与请求调用桥接，新实现应使用 ShardResolver |
| api.StorageRouteResolver | 保留包名兼容 facade，不再标记 deprecated；统一契约仍在 api.resolver.StorageRouteResolver |
| StorageRoute.direct(...)、库表便捷 getter/Builder | 保留 |
| route.routeName()/logicalTable()/attributes() | 保留，但数据来自 route.context() |
| route.shardKeyName()/shardKeyValue() | 只兼容单字段；复合字段调用会报错，应读取 context().shardKey().keys() |
| 旧平铺的上下文 Builder 方法 | 保留并弃用，构造时转换；不能与 context(...) 混用 |
| 分片编号放入 attributes | 不再注入；使用 route.shardInfo() |

兼容的是旧调用入口，不是所有自定义 SPI 实现的源代码或二进制签名。自行实现 StorageRouteResolver 的类需将方法参数改为 RouteContext；自行实现 ShardRouteResolver 的类需改为 CompositeShardKey，并迁移到 ShardResolver。需要重新编译这些实现。

由于存在旧请求重载，测试 null 参数时应明确转换成 RouteContext 或 StorageRouteRequest，避免 Java 重载歧义。旧 Object 分片值入口也受本轮明确类型集合约束，不能继续传入任意对象。

## 7. 验证与后续

```bash
mvn -pl :storage-routing-starter -am test
```

测试覆盖单字段固定落点、复合键编码与固定落点、字段边界/类型/顺序、不可变集合与上下文、旧调用桥接、
非法参数、ThreadLocal 行为、StorageRoute 到 Relational Access 的直连桥接，以及 starter 自动装配。

后续重点是技术组件 Storage 接入当前路由；当前路由链路只决定位置，不建立 JDBC Connection、改写 SQL 或创建事务。

相关图示：[模型关系](../component/01-storage-route-model.puml)、[解析时序](../sequence/03-storage-route-resolution.puml)。
