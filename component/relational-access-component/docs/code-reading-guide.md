# Relational Access V1 源码阅读路线

## 1. 先建立边界

先读：

1. `relational-mybatis/.../access/MyBatisAccess.java`
2. `relational-mybatis/.../routing/MyBatisAccessResolver.java`
3. `relational-starter/.../RelationalAccessAutoConfiguration.java`

这三处分别回答：技术组件能调用什么、如何选择运行时、单 DataSource 默认如何装配。

## 2. 单库执行

接着读 `SpringMyBatisAccess`：

- `invoke`：通过 `SqlSessionTemplate.getMapper` 获取组件 Mapper，并发送观测回调；
- 构造函数：校验 DataSource/Environment 与 Spring 事务工厂，仅解包 TransactionAware 代理；
- `execute`：无事务时普通执行；实际事务存在时也检查资源一致性；
- `executeInCurrentTransaction`：校验已有事务是否绑定当前 DataSource；
- `executeInNewTransaction`：通过 transaction-component 开启 `REQUIRES_NEW`；
- `supportsCurrentTransactionParticipation`：声明 `SqlSessionTemplate` 可加入 Spring 当前事务。

然后读 `FixedMyBatisAccessResolver`。空 key 表示默认 Access；传入非预期的非空 key 会失败，避免把错误配置
静默路由到默认库。

## 3. Direct 多库

按下面顺序阅读：

1. `DirectMyBatisResource`
2. `DirectMyBatisResourceRegistry`
3. `RoutingMyBatisAccessResolver`
4. `RoutingTransactionExecutorResolver`（transaction-component）
5. `RelationalDirectAutoConfiguration` / `RelationalDirectResourceConfiguration`
6. `DirectMyBatisSqlSessionFactoryCustomizer`
7. `IdempotencyDirectStorageAutoConfiguration`（兼容开关、幂等路由校验与事务协调）

重点观察：同一个 `DirectMyBatisResource` 从同一 DataSource 创建 `SqlSessionTemplate`、Access 与事务执行器；
Registry 再用同一组 key 同时生成 Access Resolver 和 Transaction Resolver。

## 4. Storage Routing

按模式阅读：

- Direct：`ShardIdHashStorageRouteResolver` -> `RouteMappingStrategy` -> `StorageRoute.shardedDirect`；
- ShardingSphere-JDBC：`ShardingSphereJdbcStorageRouteResolver`；
- Proxy：`ShardingSphereProxyStorageRouteResolver`；
- 作用域：`ThreadLocalStorageRouteContext`。

两个 ShardingSphere Resolver 都只校验并保留逻辑路由事实，不引用 ShardingSphere DataSource 类。

## 5. Idempotent 集成

完整调用链建议按此顺序：

1. `IdempotencyStorageRoutingAutoConfiguration`
2. `StorageRoutingIdempotencyPhysicalRouteResolver`
3. `IdempotencyPhysicalRoute`
4. `RoutedMyBatisIdempotencyRepository`
5. `MyBatisIdempotencyRepository`
6. `IdempotencyMapper`
7. `IdempotencyMapper.xml`

旧代码中的 `shardingSphereJdbcDataSourceKey` 已改名为 `shardingSphereJdbcAccessKey`。它在
`toAccessRoute` 中进入 `IdempotencyPhysicalRoute`，随后只被
`MyBatisAccessResolver.resolve` 消费；它不会传入 ShardingSphere 分片算法。

## 6. 用断点验证

Direct 模式推荐断点：

```text
StorageRoutingIdempotencyPhysicalRouteResolver.resolvePoint
RoutingMyBatisAccessResolver.resolve
SpringMyBatisAccess.requireCompatibleCurrentTransaction
IdempotencyMapper.selectForUpdate
```

ShardingSphere-JDBC 推荐断点：

```text
ShardingSphereJdbcStorageRouteResolver.resolve
StorageRoutingIdempotencyPhysicalRouteResolver.toAccessRoute
MyBatisAccessResolver.resolve
SpringMyBatisAccess.invoke
```

在最后一个断点能看到的仍是逻辑表 SQL；最终物理库表应从 ShardingSphere SQL 日志或观测能力验证，而不是从
`MyBatisAccess` 推断。

## 7. 验证测试

```bash
mvn -pl :relational-mybatis,:relational-starter -am test
mvn -pl :storage-routing-starter -am test
mvn -pl :idempotent-integration-storage-routing,:idempotent-starter -am test
```

Direct 真实双库流程见 `idempotent-storage-routing-e2e`。ShardingSphere-JDBC/Proxy 当前主要验证 Adapter 与装配
语义；真实中间件规则、物理路由和跨库事务仍需独立集成环境验证。
