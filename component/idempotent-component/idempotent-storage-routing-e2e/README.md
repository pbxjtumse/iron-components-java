# Direct datasource：幂等、路由与本地事务

本轮开发分支：`storage-routing-direct-e2e-hardening`。目标是打通 Direct 模式，不实现 ShardingSphere、XA、迁移、CDC 或自动扩容。

## 1. 保证的范围

一次幂等执行可以涉及多个业务表，但所有需要原子提交的 SQL 必须落在同一个物理 DataSource 上，并参与同一个 Spring JDBC 本地事务。

| 阶段 | 事务传播 | SQL | 提交含义 |
| --- | --- | --- | --- |
| Tx-A | REQUIRES_NEW | tryAcquire / tryRecover | 独立提交 PROCESSING 与当前 owner/version |
| Tx-B | REQUIRED | 业务回调 + result capture + markSuccess | 业务数据与 SUCCESS 一起提交或一起回滚 |
| Tx-C | REQUIRES_NEW | markFailed | Tx-B 失败后独立记录 FAILED |

StorageRouteScope 包住整个 A/B/C，但它本身不是事务。一次 execute/recover 没有外层路由时解析一次；存在外层路由时不重新 hash。对幂等表的映射可以重复执行，它只是消费相同 shardInfo，不重新计算分片键。

外层事务存在时，必须先绑定路由，再开启该资源的事务。幂等执行器返回 EXECUTED 并不代表调用方的外层事务已提交；外层随后回滚会撤销业务与 SUCCESS，而已经提交的 Tx-A 保留 PROCESSING，之后按恢复策略处理。

## 2. 新增类型与归属

| 模块 | 类型 | 职责 |
| --- | --- | --- |
| transaction-api | TransactionExecutorResolver | 按资源 key 找本地事务执行器，不依赖分片类型 |
| transaction-core | Fixed / RoutingTransactionExecutorResolver | 严格映射；空 key、未知 key 不回退默认库 |
| idempotent-integration-transaction | DirectStorageResource | 从同一 DataSource 创建事务执行器与 JDBC manager |
| idempotent-integration-transaction | DirectStorageResourceRegistry | 同一目录提供 JDBC resolver、事务 resolver 和 DataSource 列表 |
| idempotent-integration-storage-routing | StorageRouteAwareIdempotencyTransactionCoordinator | 读取绑定路由的 dataSourceKey，为 Tx-B 选择 REQUIRED 执行器 |
| idempotent-starter | IdempotencyDirectStorageAutoConfiguration | 显式开启多库装配，验证配置和资源完整性 |
| idempotent-storage-routing-e2e | DirectStorageRoutingIT | 组装真实组件、真实 MySQL、测试业务表；没有生产代码 |

IdempotencyRequest 不增加 shardKey；routeKey 仍是业务元数据/冲突校验字段，不是 dataSourceKey。CompositeShardKey 留在路由集成或业务层，幂等 api/core 不依赖它。

DirectStorageResource 当前只支持 Spring JDBC 的 DataSourceTransactionManager 本地事务。不要把 JPA、JTA、多层动态 DataSource 或分布式事务能力当成已验证范围。注册表不创建连接池、不关闭调用方的数据源，也不自动建业务表。

## 3. Spring Boot 装配

业务工程显式依赖 `idempotent-starter`、`storage-routing-starter`。业务示例使用 RelationalTemplate 时还需要 `relational-core` 和 `relational-integration-spring`。生产连接池及 MySQL 驱动由业务工程选择。

```yaml
xjtu:
  iron:
    storage-routing:
      resolver:
        enabled: true
        database-count: 10
        tables-per-database: 10
        data-source-prefix: db_
        data-source-index-width: 2
        table-prefix: business_order
        table-index-width: 2
        table-index-mode: LOCAL_TABLE_INDEX
    idempotent:
      redis:
        enabled: false
      default-windowed-repository: jdbc
      jdbc:
        direct:
          enabled: true
        routing:
          enabled: true
          logical-table: iron_idempotency_record
          table-prefix: iron_idempotency_record
      transaction:
        enabled: true
```

注册 `db_00` 到 `db_09` 共 10 个 DataSource Bean，Bean 名必须与路由输出一致。每个 key 对应唯一、稳定的 DataSource 对象；仅 URL 相同但创建了两个连接池，并不等于共享事务资源。

也可以显式提供目录，把业务已有的数据源映射交给它：

```java
@Bean
DirectStorageResourceRegistry directStorageResourceRegistry(@Qualifier("orderDataSources") Map<String, DataSource> sources) {
    return DirectStorageResourceRegistry.fromDataSources(sources);
}
```

`orderDataSources` 是业务自己定义的 Map Bean，不是组件自动创建的配置。选择显式目录后，只有这个目录中的资源参与 Direct 装配；业务持有的 DataSource 必须与目录中的是同一对象。

此模式由目录统一提供 JDBC resolver、事务 resolver、幂等 coordinator。不要再注册另一份这些类型的 Bean，也不要用 @Primary 覆盖它们；启动检查会拒绝竞争装配。单库旧模式默认不启用 direct.enabled，不受这个约束影响。

默认 resolver 已启用时，启动会检查所有配置中的 database key 是否存在；自定义 resolver 无法静态枚举其全部输出，未知 key 在访问时失败。连接可用性、schema、表和索引由部署验证/数据库运维负责，本轮不在启动时扫描全部库表。

## 4. 业务 RelationalTemplate 共用资源

以下是业务装配代码，不放进幂等 API。它严格要求 SqlRoute 中的物理 key，并在取连接之前拒绝跨库事务：

```java
@Bean
RelationalTemplate businessRelationalTemplate(DirectStorageResourceRegistry resources) {
    var provider = new SpringTransactionAwareConnectionProvider(context -> {
        var resource = resources.require(context.route().dataSourceKey());
        resource.assertCompatibleTransaction();
        return resource.dataSource();
    });
    return new DefaultRelationalTemplate(provider, new StandardSqlExceptionTranslator());
}
```

业务 Repository 按表族映射，不能把幂等物理表名直接拿来写业务 SQL：

```java
StorageRoute bound = routes.requireCurrent();
StorageRoute business = StorageRoute.builder().context(bound.context()).shardInfo(bound.shardInfo())
        .location(businessMapping.map(bound.shardInfo())).build();
String sql = "INSERT INTO " + bridge.requireTableName(business) + " (order_id, amount) VALUES (?, ?)";
relational.update(bridge.applyRoute(SqlStatement.of("order.insert", sql, orderId, amount), business));
```

`businessMapping` 由业务按自己的表前缀、库表编号规则配置。它与幂等映射必须使用相同的 datasource 拓扑与 shardInfo。物理表名只允许来自可信配置与 bridge 校验，不能直接拼接用户输入。

## 5. 两种调用方式

### 场景一：业务先绑定路由（首选）

```java
var input = RouteContext.builder().logicalTable("business_order")
        .shardKey(CompositeShardKey.of(ShardKey.of("tenant_id", tenantId), ShardKey.of("order_id", orderId))).build();
StorageRoute route = storageRouteResolver.resolve(input);
try (var scope = routes.open(route)) {
    return idempotencyExecutor.execute(IdempotencyRequest.builder().key(requestId).requestHash(requestHash).routeKey(merchantId).build(), context -> {
        orderRepository.insert(orderId, amount); // 使用上一节的路由感知 Repository
        return orderId;
    });
}
```

这里传入的 idempotencyExecutor 应为 Starter 提供的 primary 装饰器，不要绕过它直接按名字取核心执行器。业务 key 与幂等 key 可以不同；技术组件只复用业务已经确定的分片。

如需更大的外层事务，保持 route scope 在外：

```java
try (var scope = routes.open(route)) {
    return resources.require(route.dataSourceKey()).transactionExecutor().execute(tx -> {
        var result = idempotencyExecutor.execute(request, context -> orderRepository.insert(orderId, amount));
        // 如仍需执行其他同库 SQL，必须检查 result 的状态；失败时不能继续当成功提交。
        return result;
    });
}
```

### 场景二：业务不提前绑定路由

```java
return idempotencyExecutor.execute(IdempotencyRequest.of(requestId), context -> {
    orderRepository.insert(orderId, amount); // 必须使用本次已绑定的路由
    return orderId;
});
```

默认工厂使用幂等 key 生成分片键。若业务表原本按 userId/orderId 分片，不能采用此方式后又独立计算业务落点；应改用场景一。跨 shard 的相同幂等 key 不存在全局唯一约束，重试和恢复必须沿用同一分片规则/业务身份。

本地连接、事务与 ThreadLocal 路由只适用于同步同线程链路；不要在回调内直接切换到异步线程，并假定路由与事务会传播。

## 6. 测试拓扑与命令

测试使用一个临时 MySQL 8.4.4 容器、10 个独立 database、10 个 DataSource。每种编号模式创建 100 张业务表和 100 张幂等表；两种模式使用不同表前缀。

| 编号模式 | db_00 | db_01 | db_09 |
| --- | --- | --- | --- |
| LOCAL_TABLE_INDEX | 00–09 | 00–09 | 00–09 |
| GLOBAL_TABLE_INDEX | 00–09 | 10–19 | 90–99 |

幂等 DDL 从 Provider 的 `META-INF/iron-idempotency/jdbc/schema-mysql.sql` 读取，只替换可信的表名前缀。没有另造缩减版的状态表。

在仓库根目录执行：

```bash
# 单元测试与所有测试源编译；不启动 Docker
mvn -B -ntp -pl :idempotent-storage-routing-e2e -am test

# 完整 Direct 验证，需要 Docker 可用且可以拉取 MySQL 镜像
mvn -B -ntp -pl :idempotent-storage-routing-e2e -am -Pdirect-e2e verify
```

`direct-e2e` 使用 Failsafe 执行 `*IT`。没有 Docker 时失败，不以 skip 冒充验收通过；普通 test 只执行 `*Test`。测试不接受外部生产 URL，数据与建库操作只发生在测试自己创建的临时容器内。

覆盖点：两个路由入口 × 两种编号模式 × 全部 100 shard；实际业务与 markSuccess 使用相同 Connection；Tx-A 与 Tx-B 独立连接；业务失败后 Tx-C 独立记录 FAILED；外层 rollback；错库提前拒绝；requestHash/routeKey 冲突；capture 失败；stale owner 完成拒绝；DISCARDED；同分片扫描/恢复与 stale candidate；作用域清理。

GitHub workflow `Storage routing Direct E2E` 使用同一命令，保存 Surefire/Failsafe 报告。测试结果以实际 CI/本机运行记录为准；新增测试源码不等于已经验收通过。

## 7. 本轮边界与下一步

本轮不提供全库扫描调度器、跨库全局幂等索引、分布式事务或自动迁移。恢复任务需要保存原始分片定位，先绑定物理 shard，再扫描对应 scanBucket，并用 candidate 的 owner/version 调用 recover。

10 个 database 在同一容器中可验证库表选择和本地连接/事务契约，但不模拟 10 台独立 MySQL 主机的网络分区、主从延迟或故障切换；上线前需增加真实部署环境的压测、连接池容量验证和故障演练。

先让本分支所有 Direct 测试通过并评审，再合并 master。ShardingSphere Adapter 从更新后的 master 新建分支，不在此分支继续混入。
