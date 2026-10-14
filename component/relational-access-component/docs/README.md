# Relational Access V1 文档

当前版本已收口为 MyBatis-first 技术表接入层。它统一技术组件使用 MyBatis、Spring
本地事务、多 DataSource 选择与访问观测的方式，但不拥有业务 SQL，也不创建应用的连接池。

## 推荐阅读顺序

1. [architecture.md](architecture.md)：组件边界、三种部署模式，以及 `ShardingSphereDataSource` 到底归谁管理。
2. [execution-flow.md](execution-flow.md)：单库、Direct 多库、ShardingSphere-JDBC、Proxy 和幂等接入的完整流程。
3. [code-reading-guide.md](code-reading-guide.md)：从 Starter、Resolver、Access 到具体 Mapper/XML 的源码路线。
4. [L0 系统上下文图](diagrams/component/L0-system-context.puml)：先看组件之间的责任边界。
5. [L1 Access 选择](diagrams/sequence/L1-access-selection.puml)：理解 `dataSourceKey` 选择的是 Access，不一定是物理库。
6. L2 执行图：[Direct](diagrams/sequence/L2-direct-execution.puml)、[ShardingSphere-JDBC](diagrams/sequence/L2-shardingsphere-jdbc.puml)、[Proxy](diagrams/sequence/L2-shardingsphere-proxy.puml)。
7. [L3 Idempotent 集成](diagrams/sequence/L3-idempotent-route.puml)：理解 `StorageRoutingIdempotencyPhysicalRouteResolver`。
8. [L4 事务与分片边界](diagrams/sequence/L4-transaction-boundary.puml)：理解同库事务成立的条件和当前限制。

组件概览、模块清单与接入约束见 [组件 README](../README.md)。Storage Routing 的路由模型与作用域见
[Storage Routing 文档](../../storage-routing-component/docs/README.md)。

旧 `RelationalTemplate` 设计文档已随原生 JDBC 执行链一同删除，避免将已下线 API 当成现行用法。

各组件仍然保留自己的业务持久化代码：

- Repository SPI
- MyBatis Mapper
- Mapper XML
- 表结构脚本
- 状态机和 CAS 语义

但不再分别处理这些通用问题：

- 获取和管理 JDBC Connection
- PreparedStatement 参数绑定
- ResultSet 映射基础设施
- Spring 事务连接复用
- REQUIRED、REQUIRES_NEW 等事务执行方式
- 多数据源选择
- Storage Routing 物理数据源解析
- SQL 异常转换
- 重复键异常识别
- 访问监听与可观测性

Repository 的稳定扩展关系如下：

```text
reliable-task-core
  -> ReliableTaskRepository
       -> MyBatisReliableTaskRepository (V1)
       -> JdbcReliableTaskRepository    (未来可重新实现)
       -> JpaReliableTaskRepository     (未来可实现)
```

因此 V1 统一 MyBatis 并不等于永久锁死 MyBatis。Core 依赖 Repository SPI；未来增加
JDBC 或 JPA Provider 时，应新增 Provider 实现，而不是改变 Core 或复活一套与 MyBatis
并行的公共 SQL 模板。


可以。先把核心结论说死：

> Relational Access 不是 Repository、不是分片组件、也不是 ORM。  
> 它是“技术组件使用 MyBatis 访问关系型数据库时，共用的运行时执行层”。

它主要解决四件事：

1. 根据 `dataSourceKey` 找到正确的 MyBatis 运行时；
2. 统一通过 `SqlSessionTemplate` 获取 Mapper；
3. 保证 Mapper 加入正确的本地事务，或者开启 `REQUIRES_NEW`；
4. 统一访问观测，防止每个技术组件重复管理 MyBatis/JDBC。

---

# 一、先建立整体心智模型

整个调用链分成三层决策：

```text
第一层：访问哪个库、哪张表？
Storage Routing / PhysicalRouteResolver

第二层：使用哪个 MyBatis 运行时？
MyBatisAccessResolver

第三层：执行什么 SQL？
组件自己的 Repository + Mapper + Mapper XML
```

对应到 Idempotent：

```text
StorageRoutingIdempotencyPhysicalRouteResolver
    ↓
IdempotencyPhysicalRoute
    dataSourceKey
    tableName
    ↓
MyBatisAccessResolver
    ↓
SpringMyBatisAccess
    ↓
IdempotencyMapper
    ↓
IdempotencyMapper.xml
```

这几层故意分开，因为：

- “去哪”是路由问题；
- “用哪个运行时”是资源选择问题；
- “执行什么 SQL”是幂等组件自己的业务持久化问题。

Access 不应该知道：

- 什么是 `PROCESSING/SUCCESS/FAILED`；
- 什么是 `ownerToken/version`；
- 什么是 Reliable Task Lease；
- 什么是 Outbox 状态；
- 什么是订单分片算法。

---

# 二、L0：Access 在整个组件体系中的位置

对应图：

L0-system-context.puml[L0 系统上下文图](sandbox:/workspace/scratch/ef9f99730d07/iron-mybatis-fix/component/relational-access-component/docs/diagrams/component/L0-system-context.puml)

整体关系是：

```text
Idempotent / Reliable Task / Distributed Lock
                    │
                    │ 调用 Mapper
                    ▼
             Relational Access
             ├─ 选择 MyBatis 运行时
             ├─ 校验事务资源
             ├─ 开启 REQUIRES_NEW
             └─ 记录访问观测
                    │
                    ▼
             Mapper + Mapper XML
                    │
                    ▼
    普通 DataSource / Direct DataSource
    / ShardingSphere JDBC / Proxy
```

## 1. 每个技术组件保留什么

例如 Idempotent 仍然保留：

```text
IdempotencyRepository              Repository SPI
RoutedMyBatisIdempotencyRepository 路由门面
MyBatisIdempotencyRepository       单库单表实现
IdempotencyMapper                  MyBatis Mapper
IdempotencyMapper.xml              SQL
```

Access 不会把这些统一成通用 CRUD。

因为技术组件 SQL 通常不是简单增删改查：

```sql
UPDATE iron_idempotency_record
SET status = 'SUCCESS'
WHERE status = 'PROCESSING'
  AND owner_token = ?
  AND version = ?
```

这里包含幂等状态机和 CAS 语义，Access 不应该理解。

## 2. Access 统一什么

Access 统一的是：

```text
如何取得 Mapper
如何选择 DataSource 对应的运行时
如何加入当前事务
如何开启新事务
如何检查事务与 DataSource 是否匹配
如何发送访问观测事件
```

所以它处在“组件持久化代码”和“MyBatis/Spring 基础设施”中间。

---

# 三、核心接口：MyBatisAccess

源码：

:chatgpt-content-reference{index="1"}[MyBatisAccess.java](sandbox:/workspace/scratch/ef9f99730d07/iron-mybatis-fix/component/relational-access-component/relational-mybatis/src/main/java/com/xjtu/iron/relational/mybatis/access/MyBatisAccess.java)

核心接口只有四个方法：

```java
public interface MyBatisAccess {

    <M, T> T execute(
            String operationName,
            Class<M> mapperType,
            MyBatisMapperWork<M, T> work
    ) throws Exception;

    <M, T> T executeInCurrentTransaction(
            String operationName,
            Class<M> mapperType,
            MyBatisMapperWork<M, T> work
    ) throws Exception;

    <M, T> T executeInNewTransaction(
            String operationName,
            Class<M> mapperType,
            MyBatisMapperWork<M, T> work
    ) throws Exception;

    boolean supportsCurrentTransactionParticipation();
}
```

## 1. `execute`

表示：

> 直接执行 Mapper，不强制要求当前已经存在事务。

例如普通查询：

```java
access.execute(
        "idempotency.find",
        IdempotencyMapper.class,
        mapper -> mapper.select(table, storeName, namespace, key)
);
```

它可能在事务内执行，也可能不在事务内执行。

适合：

- 普通查询；
- Recovery 候选扫描；
- 不要求事务的只读操作。

## 2. `executeInCurrentTransaction`

表示：

> 当前必须已经存在本地事务，而且这个事务必须属于当前 Access 的 DataSource。

例如：

```java
access.executeInCurrentTransaction(
        "idempotency.mark-success",
        IdempotencyMapper.class,
        mapper -> mapper.complete(...)
);
```

它不会自己开启事务。

它要求业务层已经先开启了 Tx-B：

```text
BEGIN
  business_order INSERT/UPDATE
  idempotency SUCCESS CAS
COMMIT
```

如果当前没有事务，直接报错。

如果当前事务属于数据库 A，但这个 Access 属于数据库 B，也直接报错。

## 3. `executeInNewTransaction`

表示：

> 使用当前 Access 对应的 DataSource 开启一个 `REQUIRES_NEW` 事务。

例如 Tx-A：

```java
access.executeInNewTransaction(
        "idempotency.try-acquire",
        IdempotencyMapper.class,
        mapper -> tryAcquire(mapper, request)
);
```

无论外层有没有事务，都建立独立事务边界：

```text
外层事务
    ↓ 挂起
Tx-A REQUIRES_NEW
    INSERT/UPDATE PROCESSING
    COMMIT
    ↓ 恢复
外层事务
```

适合：

- 幂等抢占；
- Recovery 接管；
- 业务回滚后的 FAILED 记录；
- Reliable Task claim；
- Lease 更新。

## 4. 为什么不直接注入 Mapper

如果每个组件直接注入 Mapper：

```java
@Autowired
private IdempotencyMapper mapper;
```

单库没有明显问题。

一旦进入多库，就无法回答：

```text
这个 Mapper 当前属于 db-00 还是 db-01？
它使用哪个 SqlSessionFactory？
它加入的是哪一个事务？
当前路由和事务 DataSource 是否一致？
```

所以 Repository 不直接持有某一个固定 Mapper，而是：

```java
access.execute(
    "operation",
    IdempotencyMapper.class,
    mapper -> mapper.xxx()
);
```

Mapper 是本次调用时，由选中的 `SqlSessionTemplate` 动态取得。

---

# 四、SpringMyBatisAccess 是怎么工作的

源码：

:chatgpt-content-reference{index="2"}[SpringMyBatisAccess.java](sandbox:/workspace/scratch/ef9f99730d07/iron-mybatis-fix/component/relational-access-component/relational-mybatis/src/main/java/com/xjtu/iron/relational/mybatis/access/SpringMyBatisAccess.java)

它的对象关系非常重要：

```java
public final class SpringMyBatisAccess implements MyBatisAccess {

    private final DataSource dataSource;

    private final SqlSessionTemplate sqlSessionTemplate;

    private final TransactionExecutor transactionExecutor;

    private final MyBatisAccessListener listener;
}
```

可以把一个 `SpringMyBatisAccess` 理解为：

> 一个固定 DataSource 对应的一套 MyBatis 技术组件访问运行时。

也就是：

```text
1 SpringMyBatisAccess
    =
1 DataSource
    +
1 SqlSessionTemplate
    +
0/1 TransactionExecutor
    +
1 Listener
```

## 1. 为什么这个类可以是 Spring 单例

它内部没有保存每次请求的数据：

```text
没有 currentConnection 字段
没有 currentMapper 字段
没有 currentTransaction 字段
没有 currentRoute 字段
```

它保存的都是线程安全或者不可变依赖：

- `DataSource`：连接池通常线程安全；
- `SqlSessionTemplate`：MyBatis-Spring 提供的线程安全模板；
- `TransactionExecutor`：无状态执行器；
- `MyBatisAccessListener`：需要实现为线程安全；
- 所有字段都是 `final`。

真正的 Connection 不存储在 Access 对象里，而是由 Spring 按当前线程、当前事务管理。

因此多个线程可以同时共享一个 `SpringMyBatisAccess`。

## 2. `invoke()` 做了什么

核心逻辑可以简化为：

```java
private <M, T> T invoke(
        String operationName,
        Class<M> mapperType,
        MyBatisMapperWork<M, T> work
) throws Exception {

    listener.before(invocation);

    try {
        M mapper = sqlSessionTemplate.getMapper(mapperType);
        T result = work.execute(mapper);

        listener.afterSuccess(invocation, elapsed);
        return result;
    } catch (Exception | Error failure) {
        listener.afterFailure(invocation, elapsed, failure);
        throw failure;
    }
}
```

它做四件事：

```text
创建调用信息
    ↓
发送 before 观测
    ↓
从 SqlSessionTemplate 获取 Mapper
    ↓
执行 Repository 传入的回调
    ↓
发送 success/failure 观测
```

Access 不关心 Mapper 里面执行的是：

- `SELECT FOR UPDATE`；
- `INSERT PROCESSING`；
- `SUCCESS CAS`；
- `Lease claim`；
- `Outbox publish`。

## 3. `SqlSessionTemplate` 为什么可以共享

`SqlSessionTemplate` 并不是一个真实长期存在的数据库会话。

它更像线程安全代理：

```text
当前线程存在 Spring 事务
    -> 获取事务绑定的 SqlSession/Connection

当前线程不存在事务
    -> 创建临时 SqlSession
    -> 执行
    -> 关闭/归还
```

所以这个字段虽然被所有线程共享：

```java
private final SqlSessionTemplate sqlSessionTemplate;
```

但底层实际 Connection 不会被所有线程共享。

---

# 五、L1：dataSourceKey 到底选择什么

对应图：

L1-access-selection.puml[L1 Access 选择图](sandbox:/workspace/scratch/ef9f99730d07/iron-mybatis-fix/component/relational-access-component/docs/diagrams/sequence/L1-access-selection.puml)

流程是：

```text
Repository
    ↓
PhysicalRouteResolver
    ↓
dataSourceKey + tableName
    ↓
MyBatisAccessResolver.resolve(dataSourceKey)
    ↓
MyBatisAccess
    ↓
Mapper
```

这里有两个不同的 Resolver。

## 1. PhysicalRouteResolver

回答：

```text
目标 dataSourceKey 是什么？
目标 tableName 是什么？
```

Idempotent 对应：

```java
IdempotencyPhysicalRouteResolver
```

返回：

```java
IdempotencyPhysicalRoute.of(
        dataSourceKey,
        tableName
);
```

## 2. MyBatisAccessResolver

回答：

```text
这个 dataSourceKey 对应哪个 MyBatisAccess？
```

接口：

```java
public interface MyBatisAccessResolver {

    MyBatisAccess resolve(String dataSourceKey);

    boolean supportsCurrentTransactionParticipation();
}
```

它不计算分片。

它只维护：

```text
dataSourceKey -> MyBatisAccess
```

## 3. `dataSourceKey` 在不同模式下含义不同

| 模式 | `dataSourceKey` 含义 |
|---|---|
| 单库 | `null`，使用默认 Access |
| Direct 多库 | 物理 DataSource key |
| ShardingSphere-JDBC | 应用侧逻辑 Access key |
| Proxy | 指向 Proxy 的逻辑 Access key |

这是整个设计最容易误解的地方。

### Direct

```text
dataSourceKey = order-db-01
```

确实代表物理数据库。

### ShardingSphere-JDBC

```text
dataSourceKey = orders-sharding
```

表示：

```text
请选择绑定 ShardingSphereDataSource 的那个 Access
```

它不是 ShardingSphere 内部：

```text
ds_0
ds_1
ds_2
```

### Proxy

```text
dataSourceKey = orders-proxy
```

表示：

```text
请选择 JDBC URL 指向 Proxy 的那个 Access
```

后端物理库由 Proxy 决定。

---

# 六、FixedMyBatisAccessResolver：普通单库

普通单库结构是：

```text
FixedMyBatisAccessResolver
    ↓
唯一 SpringMyBatisAccess
    ↓
唯一 DataSource
```

核心逻辑：

```java
public MyBatisAccess resolve(String requestedDataSourceKey) {
    String requested = normalize(requestedDataSourceKey);

    if (!Objects.equals(dataSourceKey, requested)
            && requested != null) {
        throw new IllegalArgumentException(...);
    }

    return access;
}
```

普通默认 DataSource：

```java
FixedMyBatisAccessResolver.defaultDataSource(access);
```

它的固定 key 是 `null`。

所以：

```java
resolver.resolve(null);
```

返回默认 Access。

但如果错误地传入：

```java
resolver.resolve("db-01");
```

它不会静默返回默认库，而是报错。

这是故意的。

否则配置错误可能导致：

```text
本来应该写 db-01
结果悄悄写入默认库
```

这种错误比直接启动失败危险得多。

---

# 七、L2：Direct 多库是怎么实现的

对应图：

L2-direct-execution.puml[L2 Direct 执行图](sandbox:/workspace/scratch/ef9f99730d07/iron-mybatis-fix/component/relational-access-component/docs/diagrams/sequence/L2-direct-execution.puml)

Direct 模式表示：

> 应用直接持有多个物理 DataSource。

例如：

```text
order-db-00 -> HikariDataSource A
order-db-01 -> HikariDataSource B
order-db-02 -> HikariDataSource C
```

这时候不能只创建一个 Access。

必须是：

```text
order-db-00 -> Access A + TransactionExecutor A
order-db-01 -> Access B + TransactionExecutor B
order-db-02 -> Access C + TransactionExecutor C
```

## 1. DirectMyBatisResource

源码：

:chatgpt-content-reference{index="5"}[DirectMyBatisResource.java](sandbox:/workspace/scratch/ef9f99730d07/iron-mybatis-fix/component/relational-access-component/relational-mybatis/src/main/java/com/xjtu/iron/relational/mybatis/resource/DirectMyBatisResource.java)

一个 `DirectMyBatisResource` 表示：

```text
一个物理 DataSource 对应的完整运行时资源
```

它内部包含：

```java
private final String dataSourceKey;
private final DataSource dataSource;
private final DirectTransactionManager transactionManager;
private final TransactionExecutor transactionExecutor;
private final MyBatisAccess myBatisAccess;
```

关系是：

```text
DirectMyBatisResource(db-01)
    ├─ DataSource(db-01)
    ├─ DataSourceTransactionManager(db-01)
    ├─ TransactionExecutor(db-01)
    ├─ SqlSessionTemplate(db-01)
    └─ SpringMyBatisAccess(db-01)
```

关键不是“创建了很多对象”，而是：

> 这些对象全部来自同一个 DataSource。

这样可以避免：

```text
路由选中了 db-01
Mapper 使用 db-01
事务管理器却管理 db-00
```

## 2. 为什么同时生成 Access Resolver 和 Transaction Resolver

`DirectMyBatisResourceRegistry` 会从相同资源集合创建：

```java
this.myBatisAccessResolver =
        new RoutingMyBatisAccessResolver(null, accesses);

this.transactionExecutorResolver =
        new RoutingTransactionExecutorResolver(executors);
```

因此同一个 key：

```text
db-01
```

同时映射到：

```text
MyBatisAccess(db-01)
TransactionExecutor(db-01)
```

调用链如下：

```text
StorageRoute.dataSourceKey = db-01
        │
        ├─ MyBatisAccessResolver.resolve(db-01)
        │       -> MyBatisAccess(db-01)
        │
        └─ TransactionExecutorResolver.resolve(db-01)
                -> TransactionExecutor(db-01)
```

Access 和 Transaction 不会各自维护两份可能不一致的配置。

## 3. Direct 实际执行过程

假设：

```text
shardKey = orderId=123
```

路由结果：

```text
dataSourceKey = order-db-01
tableName = iron_idempotency_record_03
```

完整过程：

```text
1. Repository 获得物理路由
   order-db-01 + iron_idempotency_record_03

2. AccessResolver.resolve("order-db-01")

3. 返回 SpringMyBatisAccess(order-db-01)

4. Access 获取 IdempotencyMapper

5. Mapper XML 中 ${table}
   被替换为经过验证的 iron_idempotency_record_03

6. SQL 通过 order-db-01 对应的 SqlSessionTemplate 执行

7. 最终进入 order-db-01
```

## 4. 为什么必须“先绑定路由，再开启事务”

错误顺序：

```text
先开启 db-00 事务
    ↓
再把路由切换到 db-01
    ↓
幂等组件尝试使用 db-01
```

此时 `DirectMyBatisResource.assertCompatibleTransaction()` 会检查：

```java
if (TransactionSynchronizationManager.isActualTransactionActive()
        && !transactionManager.hasBoundTransaction()) {
    throw new IllegalStateException(...);
}
```

也就是：

```text
当前线程有事务
但不是当前 db-01 的事务
```

直接失败。

正确顺序：

```java
try (StorageRouteScope scope = routeContext.open(routeDb01)) {
    transactionExecutorDb01.execute(...);
}
```

即：

```text
先确定数据库
再根据数据库选择事务执行器
最后开始事务
```

---

# 八、L2：ShardingSphere-JDBC 为什么只有一个 Access

对应图：

L2-shardingsphere-jdbc.puml[L2 ShardingSphere-JDBC](sandbox:/workspace/scratch/ef9f99730d07/iron-mybatis-fix/component/relational-access-component/docs/diagrams/sequence/L2-shardingsphere-jdbc.puml)

结构是：

```text
SpringMyBatisAccess
    ↓
SqlSessionTemplate
    ↓
ShardingSphereDataSource
    ├─ ds_0
    ├─ ds_1
    └─ ds_2
```

Access 看到的是一个逻辑 DataSource：

```text
ShardingSphereDataSource
```

ShardingSphereDataSource 内部才有多个物理库。

所以：

```text
Access 数量 = 1
物理数据库数量 = N
```

完全不矛盾。

## 执行过程

```text
Repository
    ↓
AccessResolver.resolve("orders-sharding")
    ↓
SpringMyBatisAccess
    ↓
IdempotencyMapper
    ↓
逻辑 SQL：
INSERT INTO iron_idempotency_record (...)
VALUES (..., idempotency_key, ...)
    ↓
ShardingSphere 解析 SQL
    ↓
根据 idempotency_key 计算物理分片
    ↓
ds_01.iron_idempotency_record_03
```

## `shardingSphereJdbcAccessKey` 的作用

代码中的：

```java
private final String shardingSphereJdbcAccessKey;
```

只负责：

```java
accessResolver.resolve(shardingSphereJdbcAccessKey);
```

它不会传给 ShardingSphere 分片算法。

真正决定 ShardingSphere 分片的是：

```text
逻辑表
SQL 中的分片列
ShardingSphere 分片规则
```

## 当前真实缺口

Storage Routing 中虽然保存了：

```text
RouteContext
CompositeShardKey
StorageRoute
```

但目前没有自动执行：

```java
HintManager.addDatabaseShardingValue(...);
```

因此：

> StorageRouteContext 里有 shardKey，不代表 ShardingSphere 自动知道这个值。

当前点查 SQL 包含：

```sql
WHERE idempotency_key = ?
```

如果 ShardingSphere 也按 `idempotency_key` 分片，就能精确路由。

Recovery SQL主要按：

```sql
WHERE scan_bucket = ?
```

如果 ShardingSphere 不是按 `scan_bucket` 路由，则可能广播扫描。

这一块现在不能假装已经完全打通。

---

# 九、L2：Proxy 模式

对应图：

L2-shardingsphere-proxy.puml[L2 ShardingSphere-Proxy](sandbox:/workspace/scratch/ef9f99730d07/iron-mybatis-fix/component/relational-access-component/docs/diagrams/sequence/L2-shardingsphere-proxy.puml)

结构是：

```text
SpringMyBatisAccess
    ↓
普通 HikariDataSource
    ↓ JDBC
ShardingSphere-Proxy
    ├─ ds_0
    └─ ds_1
```

应用内部没有 `ShardingSphereDataSource`。

应用看到的就是一个普通 JDBC 地址：

```text
jdbc:mysql://shardingsphere-proxy:3307/order_db
```

流程：

```text
Mapper 生成逻辑 SQL
    ↓
普通 DataSource 发送给 Proxy
    ↓
Proxy 解析 SQL
    ↓
Proxy 选择后端物理库表
```

所以 Proxy 模式的 Access 仍然可能只有一个。

---

# 十、L3：Idempotent 如何接入 Access

对应图：

L3-idempotent-route.puml[L3 Idempotent 路由图](sandbox:/workspace/scratch/ef9f99730d07/iron-mybatis-fix/component/relational-access-component/docs/diagrams/sequence/L3-idempotent-route.puml)

核心入口：

```java
RoutedMyBatisIdempotencyRepository
```

它有三个共享字段：

```java
private final IdempotencyPhysicalRouteResolver routeResolver;

private final MyBatisAccessResolver accessResolver;

private final ConcurrentMap<
        IdempotencyPhysicalRoute,
        MyBatisIdempotencyRepository
> delegates = new ConcurrentHashMap<>();
```

## 1. 先解析物理目标

```java
IdempotencyPhysicalRoute route =
        routeResolver.resolvePoint(
                storageContext,
                namespace,
                key
        );
```

得到：

```text
dataSourceKey
tableName
```

## 2. 再选择 Access

```java
MyBatisAccess access =
        accessResolver.resolve(route.getDataSourceKey());
```

## 3. 创建单目标 Repository

```java
new MyBatisIdempotencyRepository(
        access,
        route.getTableName()
);
```

这里的 `MyBatisIdempotencyRepository` 可以理解为：

> 已经绑定某一个 Access 和某一张表的幂等 Repository。

## 4. 为什么缓存 delegate

```java
delegates.computeIfAbsent(
        route,
        current -> new MyBatisIdempotencyRepository(...)
);
```

例如：

```text
(order-db-01, iron_idempotency_record_03)
    -> MyBatisIdempotencyRepository A

(order-db-01, iron_idempotency_record_04)
    -> MyBatisIdempotencyRepository B
```

这些 Repository 是无状态或只保存不可变依赖的，可以复用。

缓存的不是：

- Connection；
- SqlSession；
- Transaction；
- 当前请求。

缓存的是：

```text
Access 引用 + 已验证表名
```

因此使用 `ConcurrentHashMap` 是安全的。

---

# 十一、StorageRoutingIdempotencyPhysicalRouteResolver 的分支逻辑

这个类主要处理三种情况。

## 1. 外层已经绑定 Direct 分片路由

例如业务已经绑定：

```text
业务表：
db-01.business_order_03
shardInfo：
databaseIndex=1
tableIndex=3
```

幂等组件不能直接使用业务表名。

它应该：

```text
复用 shardInfo
    ↓
使用幂等表自己的 mappingStrategy
    ↓
得到：
db-01.iron_idempotency_record_03
```

所以是：

```text
共享分片身份
不共享物理表名
```

并且会校验：

```java
if (!Objects.equals(
        mapped.dataSourceKey(),
        bound.dataSourceKey()
)) {
    throw new StorageRoutingException(...);
}
```

保证：

```text
业务表在 db-01
幂等表也必须在 db-01
```

否则 Tx-B 无法使用同一个本地事务。

## 2. 当前没有业务路由

默认根据幂等 key 产生 shard key：

```java
CompositeShardKey.of(
    ShardKey.of("idempotencyKey", idempotencyKey)
);
```

再调用全局 `StorageRouteResolver`。

## 3. Middleware 模式

如果当前模式是：

```text
SHARDING_SPHERE_JDBC
PROXY
```

它不产生物理表名，而是返回：

```text
逻辑 Access key
幂等逻辑表名
```

后续交给 ShardingSphere。

---

# 十二、L4：事务为什么能够共用同一个 Connection

对应图：

L4-transaction-boundary.puml[L4 事务边界](sandbox:/workspace/scratch/ef9f99730d07/iron-mybatis-fix/component/relational-access-component/docs/diagrams/sequence/L4-transaction-boundary.puml)

这是 Access 最关键的价值。

## Tx-B 理想过程

```text
TransactionCoordinator
    ↓
选择 db-01 TransactionExecutor
    ↓
BEGIN db-01
    ↓
业务 Mapper 写 business_order
    ↓
Idempotent markSuccess
    ↓
SpringMyBatisAccess.executeInCurrentTransaction
    ↓
IdempotencyMapper SUCCESS CAS
    ↓
COMMIT
```

业务 Mapper 和幂等 Mapper 可以来自不同 `SqlSessionTemplate`，但只要它们：

```text
使用同一个 DataSource 实例
使用 Spring 事务体系
```

就能通过 `DataSourceUtils` 复用线程上绑定的 Connection。

## `requireCompatibleCurrentTransaction()` 三层检查

### 第一层：是否真的存在事务

```java
TransactionSynchronizationManager
        .isActualTransactionActive()
```

防止把只有 synchronization、没有真实事务的情况误认为事务。

### 第二层：事务是否绑定当前 DataSource

```java
TransactionSynchronizationManager
        .hasResource(dataSource)
```

如果当前事务属于 db-00，而 Access 属于 db-01，这里失败。

### 第三层：Connection 是否真属于当前事务

```java
Connection connection =
        DataSourceUtils.getConnection(dataSource);

DataSourceUtils.isConnectionTransactional(
        connection,
        dataSource
);
```

最后确认取得的 Connection 是事务管理器绑定的 Connection。

`releaseConnection()` 在事务环境下不会真的关闭连接，只是交还给 Spring 管理。

---

# 十三、Tx-A、Tx-B、Tx-C 如何使用 Access

| 阶段 | Access 方法 | 作用 |
|---|---|---|
| Tx-A | `executeInNewTransaction` | 抢占并写入 `PROCESSING` |
| Tx-B | `executeInCurrentTransaction` | 业务 SQL 与 `SUCCESS` CAS 原子提交 |
| Tx-C | `executeInNewTransaction` | 业务事务失败后独立记录 `FAILED` |

完整流程：

```text
StorageRoute = db-01 / shard-03
        ↓
Tx-A REQUIRES_NEW
        ↓
tryAcquire
        ↓
PROCESSING(owner=A, version=1)
        ↓ COMMIT

Tx-B REQUIRED
        ↓
业务 SQL
        ↓
SUCCESS CAS(owner=A, version=1)
        ↓
COMMIT
```

如果业务失败：

```text
Tx-B
    业务 SQL
    ↓
异常
    ↓
ROLLBACK

Tx-C REQUIRES_NEW
    ↓
FAILED CAS(owner=A, version=1)
    ↓
COMMIT
```

如果 `SUCCESS CAS` 更新 0 行：

```text
旧 owner 已失效
或者 version 已变化
```

此时 Tx-B 必须整体回滚，业务 SQL 不能提交。

---

# 十四、同样都使用 MyBatis，为什么仍可能事务不一致

下面两个 Mapper虽然都用 MyBatis：

```text
BusinessMapper -> DataSource A
IdempotencyMapper -> DataSource B
```

仍然无法共享本地事务。

决定本地事务边界的是：

```text
DataSource / Connection
```

不是：

```text
是否都使用 MyBatis
```

正确条件是：

```text
业务 Mapper
    ↓
DataSource A

幂等 Access
    ↓
DataSource A

TransactionExecutor
    ↓
DataSourceTransactionManager(A)
```

三者必须落在同一个事务资源上。

---

# 十五、这套设计使用了哪些设计模式

## 1. Repository Pattern

技术组件 Core 依赖：

```text
IdempotencyRepository
ReliableTaskRepository
```

而不是依赖 MyBatis。

## 2. Adapter Pattern

```text
SpringMyBatisAccess
```

把：

```text
SqlSessionTemplate
Spring Transaction
Transaction Component
```

适配成技术组件统一执行入口。

## 3. Strategy Pattern

```text
MyBatisAccessResolver
IdempotencyPhysicalRouteResolver
StorageRouteResolver
```

都是根据不同模式选择策略。

## 4. Registry Pattern

```text
DirectMyBatisResourceRegistry
```

维护：

```text
dataSourceKey -> 完整资源
```

## 5. Template/Callback

```java
access.execute(
    operationName,
    Mapper.class,
    mapper -> ...
);
```

Access 固定执行骨架，Repository 提供具体 Mapper 操作。

---

# 十六、为什么删除自研 RelationalTemplate 是正确的

原来的方向相当于自己维护：

```text
Connection 获取
PreparedStatement 创建
参数绑定
ResultSet 映射
异常转换
事务连接复用
```

这实际上是在重新实现一部分 MyBatis/Spring JDBC。

现在变成：

```text
技术组件 Provider
    -> Mapper
    -> Mapper XML
    -> MyBatis
```

Access 只补齐技术组件共同需要，但 MyBatis 本身没有提供的部分：

```text
多 Access 选择
事务资源一致性检查
REQUIRES_NEW
统一观测
Direct 资源注册
```

这个边界更合理。

---

# 十七、未来换 JPA/JDBC，是否会被 MyBatis 锁死

不会，但要理解扩展位置。

Core 依赖的是：

```text
IdempotencyRepository
ReliableTaskRepository
```

而不是 `MyBatisAccess`。

未来增加 JPA：

```text
IdempotencyRepository
    ├─ MyBatisIdempotencyRepository
    └─ JpaIdempotencyRepository
```

未来增加原生 JDBC：

```text
IdempotencyRepository
    ├─ MyBatisIdempotencyRepository
    ├─ JpaIdempotencyRepository
    └─ JdbcIdempotencyRepository
```

`MyBatisAccess` 本身就是 MyBatis 专用能力，不需要硬抽象成同时兼容 JPA/JDBC 的万能接口。

否则最后又会退化成一个自研 ORM。

---

# 十八、当前设计仍然存在的真实问题

整体方向正确，但 V1 还不能说完全收口。

## 1. Direct 自动配置目前放在 Idempotent Starter

公共类已经在 Access：

```text
DirectMyBatisResource
DirectMyBatisResourceRegistry
```

但自动创建注册表的代码目前在：

```text
IdempotencyDirectStorageAutoConfiguration
```

这意味着第一消费者仍然是 Idempotent。

以后 Reliable Task、Outbox 都使用 Direct 多库后，更合理的做法是：

```text
relational-direct-starter
或者
relational-starter 中独立 Direct 自动配置
```

避免其他组件依赖 Idempotent Starter 才能获得 Direct 资源。

## 2. Direct 自建 SqlSessionFactory 没有继承应用完整 MyBatis 配置

当前代码：

```java
SqlSessionFactoryBean factoryBean =
        new SqlSessionFactoryBean();

factoryBean.setDataSource(dataSource);
factoryBean.setMapperLocations(...);
```

它主要加载 Iron Mapper XML，但没有自动复制应用的：

- MyBatis plugins；
- interceptors；
- type handlers；
- configuration customizers；
- naming 配置。

当前技术表类型简单，暂时能工作。

但后续应提供公共定制入口，而不是无限在构造函数里增加参数。

## 3. Direct 模式观测目前使用 noop

Direct 资源中现在是：

```java
MyBatisAccessListener.noop()
```

所以普通单库 Starter 的组合 Listener 并没有自动进入 Direct Access。

如果我们准备完善观测，这里需要改为注入 Listener。

## 4. ShardingSphere 还缺真实 Hint/规则 E2E

当前已经完成：

```text
模式表达
逻辑表交付
Access 选择
```

尚未完整完成：

```text
StorageRoute -> HintManager
真实 ShardingSphere 规则
Recovery 定向扫描
跨物理库事务验证
```

## 5. `supportsCurrentTransactionParticipation()` 比较乐观

`SpringMyBatisAccess` 目前直接返回：

```java
return true;
```

它表达的是：

```text
SqlSessionTemplate 具备加入 Spring 事务的能力
```

但不代表某一次调用一定路由到了正确 DataSource。

真正的安全保障仍然是运行时：

```java
requireCompatibleCurrentTransaction();
```

---

# 十九、把完整流程串成一个实际例子

假设订单号：

```text
orderId = 12345
```

路由结果：

```text
db-01.business_order_03
```

幂等表映射：

```text
db-01.iron_idempotency_record_03
```

完整链路：

```text
1. StorageRouteResolver 计算业务路由
   -> db-01 / shardInfo(1,3)

2. StorageRouteContext 绑定路由

3. 幂等 tryAcquire

4. StorageRoutingIdempotencyPhysicalRouteResolver
   -> 复用 shardInfo
   -> 重新映射幂等表
   -> db-01 / iron_idempotency_record_03

5. MyBatisAccessResolver.resolve("db-01")
   -> SpringMyBatisAccess(db-01)

6. executeInNewTransaction
   -> Tx-A
   -> SELECT FOR UPDATE / INSERT PROCESSING
   -> COMMIT

7. TransactionCoordinator 根据 routeKey 选择
   -> TransactionExecutor(db-01)

8. 开启 Tx-B

9. 业务 MyBatis Mapper
   -> 写入 business_order_03

10. markSuccess

11. 再次解析到
    -> db-01 / iron_idempotency_record_03

12. SpringMyBatisAccess.executeInCurrentTransaction

13. 校验当前事务确实绑定 db-01

14. IdempotencyMapper.complete
    -> SUCCESS CAS(ownerToken, version)

15. CAS 成功
    -> 一起 COMMIT

16. CAS 失败
    -> 业务 SQL 和幂等 SQL 一起 ROLLBACK
```

这就是 Access 组件存在的真正价值：

> 它不是帮我们少写几行 Mapper，而是保证“路由选中的数据库、MyBatis 执行使用的数据库、事务管理的数据库”三者保持一致。

推荐你接下来按这个顺序看代码：

1. :chatgpt-content-reference{index="10"}[MyBatisAccess](sandbox:/workspace/scratch/ef9f99730d07/iron-mybatis-fix/component/relational-access-component/relational-mybatis/src/main/java/com/xjtu/iron/relational/mybatis/access/MyBatisAccess.java)
2. :chatgpt-content-reference{index="11"}[SpringMyBatisAccess](sandbox:/workspace/scratch/ef9f99730d07/iron-mybatis-fix/component/relational-access-component/relational-mybatis/src/main/java/com/xjtu/iron/relational/mybatis/access/SpringMyBatisAccess.java)
3. :chatgpt-content-reference{index="12"}[DirectMyBatisResource](sandbox:/workspace/scratch/ef9f99730d07/iron-mybatis-fix/component/relational-access-component/relational-mybatis/src/main/java/com/xjtu/iron/relational/mybatis/resource/DirectMyBatisResource.java)
4. :chatgpt-content-reference{index="13"}[DirectMyBatisResourceRegistry](sandbox:/workspace/scratch/ef9f99730d07/iron-mybatis-fix/component/relational-access-component/relational-mybatis/src/main/java/com/xjtu/iron/relational/mybatis/resource/DirectMyBatisResourceRegistry.java)
5. :chatgpt-content-reference{index="14"}[RoutedMyBatisIdempotencyRepository](sandbox:/workspace/scratch/ef9f99730d07/iron-mybatis-fix/component/idempotent-component/idempotent-provider/idempotent-provider-mybatis/src/main/java/com/xjtu/iron/idempotent/provider/mybatis/repository/RoutedMyBatisIdempotencyRepository.java)
6. :chatgpt-content-reference{index="15"}[MyBatisIdempotencyRepository](sandbox:/workspace/scratch/ef9f99730d07/iron-mybatis-fix/component/idempotent-component/idempotent-provider/idempotent-provider-mybatis/src/main/java/com/xjtu/iron/idempotent/provider/mybatis/repository/MyBatisIdempotencyRepository.java)
7. 最后看 `IdempotencyMapper.xml`

如果只记住一句话：

> Storage Routing 决定去哪里，Access 保证用正确的 MyBatis 和事务访问那里，Repository/Mapper 决定在那里执行什么 SQL。