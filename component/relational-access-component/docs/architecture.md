# Relational Access V1 架构

## 1. 一句话结论

Relational Access 是技术组件共享的 **MyBatis 执行与本地事务接入层**。它接收应用已经准备好的
`DataSource` / `SqlSessionTemplate`，向 Idempotent、Reliable Task、Distributed Lock 等组件提供稳定的
`MyBatisAccess`；它不创建 ShardingSphere 规则，不计算业务分片，也不拥有组件 Mapper SQL。

最重要的三个问题必须分开：

| 问题 | 负责者 | 典型类型 |
| --- | --- | --- |
| 这次访问属于哪个分片、物理库表或中间件模式？ | Storage Routing | `StorageRouteResolver`、`StorageRoute` |
| 使用哪个 MyBatis 运行时和本地事务资源？ | Relational Access | `MyBatisAccessResolver`、`SpringMyBatisAccess` |
| 技术表执行什么 SQL、如何做状态 CAS？ | 各技术组件 Provider | Repository、Mapper、Mapper XML |

## 2. `ShardingSphereDataSource` 归谁管理

`ShardingSphereDataSource` **不在 Relational Access 中创建或维护**。

在 ShardingSphere-JDBC 模式下，应用或 ShardingSphere Spring Boot 集成先创建一个逻辑
`DataSource`。这个逻辑 DataSource 内部持有多套物理 DataSource 与分片规则。MyBatis 再基于这个逻辑
DataSource 创建 `SqlSessionFactory` / `SqlSessionTemplate`。Relational Access 最后只是复用这两个 Spring Bean：

```text
application / ShardingSphere configuration
  -> logical ShardingSphere DataSource
       -> physical ds_0
       -> physical ds_1
  -> SqlSessionTemplate
  -> SpringMyBatisAccess
```

所以“一份 Access”只表示应用侧有一个逻辑访问入口，不表示后端只有一个物理数据库。

在 ShardingSphere-Proxy 模式下，应用内甚至没有 `ShardingSphereDataSource`。应用创建的是普通 JDBC
连接池，连接地址指向 Proxy；物理库、分片规则和 SQL 路由都在 Proxy 进程中。

## 3. `shardingSphereJdbcDataSourceKey` 的真实含义

此前 `StorageRoutingIdempotencyPhysicalRouteResolver` 中的私有字段名
`shardingSphereJdbcDataSourceKey` 容易被误解。本次已改名为 `shardingSphereJdbcAccessKey`；外部配置名仍保持
`sharding-sphere-jdbc.data-source-key`。它的准确含义是：

> 当应用存在多个**逻辑 DataSource / MyBatis Access** 时，用哪个 key 从
> `MyBatisAccessResolver` 中选出承载 ShardingSphere-JDBC 的 Access。

它不是：

- ShardingSphere 内部 `ds_0`、`ds_1` 等物理分片名；
- 分片算法输入；
- 创建 `ShardingSphereDataSource` 的配置；
- 让 `StorageRouteContext` 自动变成 ShardingSphere Hint 的开关。

只有一个逻辑 DataSource 时，该配置留空，`FixedMyBatisAccessResolver` 返回默认 Access：

```java
MyBatisAccess access = accessResolver.resolve(null);
```

应用同时存在订单 ShardingSphere DataSource、用户 ShardingSphere DataSource 等多个逻辑入口时，应用需要显式
注册 `RoutingMyBatisAccessResolver`，然后配置对应键：

```java
Map<String, MyBatisAccess> accesses = Map.of(
        "orders-sharding", ordersAccess,
        "users-sharding", usersAccess
);
MyBatisAccessResolver resolver = new RoutingMyBatisAccessResolver(null, accesses);
```

```yaml
xjtu:
  iron:
    storage-routing:
      mode: SHARDING_SPHERE_JDBC
      sharding-sphere-jdbc:
        data-source-key: orders-sharding
```

当前 `relational-starter` 只在 Spring 容器中存在唯一候选 `DataSource` 时自动创建默认 Access；它不会猜测多个
逻辑 DataSource 应如何命名和配对。多逻辑 DataSource 的 `SqlSessionTemplate`、`MyBatisAccess` 和 Resolver
应由应用显式装配。

## 4. 四种实际拓扑

| 场景 | 应用侧 Access 数 | Access 绑定对象 | 谁选物理库 | SQL 表名 |
| --- | ---: | --- | --- | --- |
| 普通单库 | 1 | 普通 DataSource | 固定单库 | 固定表 |
| Direct 多库 | N，每个物理库一个 | N 个物理 DataSource | Storage Routing + Access Resolver | 物理表 |
| ShardingSphere-JDBC | 通常 1 个逻辑 Access | ShardingSphere 逻辑 DataSource | ShardingSphere-JDBC | 逻辑表 |
| ShardingSphere-Proxy | 通常 1 个逻辑 Access | 指向 Proxy 的普通 DataSource | Proxy 服务端 | 逻辑表 |

Direct 和 ShardingSphere-JDBC 的关键区别不是“是否多库”，而是应用的 Access 是否直接对应物理库。

## 5. 模块与依赖方向

```text
technical component core
  -> component Repository SPI

component MyBatis provider
  -> component Repository SPI
  -> component Mapper / Mapper XML
  -> relational-mybatis

relational-starter
  -> application DataSource
  -> application SqlSessionTemplate
  -> transaction-component
```

`relational-mybatis` 不依赖 Idempotent、Reliable Task 或 Storage Routing。反向依赖保证 Access 不知道
`ownerToken`、Lease、Recovery、Outbox 等业务语义。

## 6. 事务边界

`SpringMyBatisAccess` 提供三种执行语义：

| API | 语义 |
| --- | --- |
| `execute` | 无事务时普通执行；有实际事务时校验当前 DataSource 资源绑定 |
| `executeInCurrentTransaction` | 必须已有本地事务，且该事务绑定的是当前 Access 的 DataSource |
| `executeInNewTransaction` | 使用同一 DataSource 的 `TransactionExecutor` 开启 `REQUIRES_NEW` |

它能保证“Mapper 与选中的 DataSource/事务资源一致”，但不能把跨物理库事务自动变成本地事务。对于
ShardingSphere-JDBC，如果一次事务跨越多个实际数据库，是否原子提交由 ShardingSphere 的分布式事务配置决定，
不由 Relational Access 保证。Iron 默认设计仍是使用同一个业务分片键，让业务表、幂等表、Outbox 表落到同一
物理数据库，从而使用普通本地事务。

## 7. 当前实现边界与待办

- Access 构造时校验 DataSource/Environment 配对和 Spring 事务工厂，仅解包 TransactionAware 代理。
- Direct 公共装配由 `relational-starter` 提供，使用 `xjtu.iron.relational.direct.enabled=true` 显式启用；
  旧幂等 Direct 配置导入同一资源配置，保留组件特有的事务与路由校验。
- `DirectMyBatisSqlSessionFactoryCustomizer` 提供每库工厂定制；公共装配注入访问 Listener，
  Access 隔离监听器 RuntimeException。它不自动复制业务应用的全部 MyBatis 配置。
- ShardingSphere Adapter 当前只标记模式、保留逻辑表和分片键，不创建 DataSource、不复制规则。
- Repository SQL 必须显式包含 ShardingSphere 配置使用的分片列；`StorageRouteContext` 不会自动改写 SQL。
- 当前没有把 `StorageRoute` 自动转换成 ShardingSphere `HintManager` 的公共实现。
- Idempotent 点查 SQL 已携带 `idempotency_key`；前提是 ShardingSphere 规则也使用可由该列确定的分片策略。
- Recovery 查询主要按 `scan_bucket` 扫描。若规则不能由 SQL 中的条件确定目标分片，可能广播路由；V1 不能把
  “外层绑定了 middleware route”误写成“已经完成定向物理分片扫描”。
- Proxy 的部署、规则、分布式事务与高可用均属于 Proxy 运维边界。

这些限制不影响 Direct 链路；Direct 已经把物理 `dataSourceKey + tableName` 解析并交给 Access。
