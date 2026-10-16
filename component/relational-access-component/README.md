# Relational Access Component

Relational Access V1 是所有关系型技术表的 MyBatis 公共接入层。它不是 ORM，也不再提供通用 SQL/RowMapper 模板。

完整的架构边界、四种拓扑、L0-L4 图、执行流程与源码路线见 [docs/README.md](docs/README.md)。

## 设计边界

- 每个业务技术组件保留自己的 Repository SPI、Mapper 接口和 Mapper XML。
- `relational-mybatis` 只统一 Mapper 获取、Spring 本地事务资源校验、`REQUIRES_NEW`、多 DataSource 解析和观测回调。
- `relational-starter` 复用应用的 `DataSource` 和 `SqlSessionTemplate`；单库时提供默认 `MyBatisAccessResolver`。
- Storage Routing 只决定访问哪个库表；Repository 再使用 `MyBatisAccessResolver` 选择对应 MyBatis 运行时。
- 不在 Access 内抽象 CRUD、不包装业务 Mapper、不屏蔽各组件的状态迁移 SQL。

## 模块

| 模块 | 职责 |
| --- | --- |
| `relational-mybatis` | `MyBatisAccess`、Access Resolver、Direct 多数据源资源目录、事务校验、表名/唯一键公共工具 |
| `relational-starter` | 单库默认 Access、本地事务适配，以及显式启用的 Direct 公共资源装配 |

`relational-mybatis` 按职责分包，避免公共入口继续平铺：

| 包 | 职责 |
| --- | --- |
| `access` | Mapper 执行入口与 Spring MyBatis 实现 |
| `routing` | 单库和多库 Access 解析 |
| `resource` | Direct DataSource、事务执行器和 MyBatis 运行时注册表 |
| `observation` | 访问调用模型与监听器 |
| `support` | 动态表名与约束冲突等公共校验 |

已删除旧 `relational-api` / `relational-spi` / `relational-core` / `relational-integration-spring`。因此不再存在 `RelationalTemplate`、`JdbcStatementConfigurer`、`JdbcParameterBinder` 等自研 JDBC 执行链。

## 单库调用链

```text
Component Repository
  -> MyBatisAccessResolver
  -> SpringMyBatisAccess
  -> SqlSessionTemplate.getMapper(ComponentMapper)
  -> Component Mapper XML
  -> application DataSource
```

`relational-starter` 在存在唯一 DataSource 时自动组装该链路。应用已提供 `TransactionExecutor` 时直接复用；未提供时，Starter 用同一 DataSource 创建 `DataSourceTransactionManager` 适配，保证技术组件的 `REQUIRES_NEW` 语义可用。

## Direct 多库

应用可独立启用公共资源装配，DataSource Bean 名作为 `dataSourceKey`：

```yaml
xjtu:
  iron:
    relational:
      direct:
        enabled: true
```

`RelationalDirectAutoConfiguration` 导入公共资源配置，并在缺少自定义解析器时提供
`MyBatisAccessResolver` 与 `TransactionExecutorResolver`。旧的
`xjtu.iron.idempotent.mybatis.direct.enabled=true` 继续通过同一公共配置创建目录，
幂等的路由校验与事务协调仍归幂等集成负责。

`DirectMyBatisResourceRegistry` 对每个 DataSource 同时创建：

- 一个 `SqlSessionTemplate`；
- 一个与该 DataSource 绑定的 `TransactionExecutor`；
- 一个 `MyBatisAccess`。

这三者来自同一份资源目录，防止“路由选中库 A，事务却由库 B 管理”。当前线程已有其他 DataSource 的事务时，目录会拒绝跨库冒充本地事务。

Direct 运行时扫描 `classpath*:com/xjtu/iron/**/mybatis/**/*.xml`，因此组件 Mapper XML
必须位于 `com/xjtu/iron` 命名空间下包含 `mybatis` 的目录。该规则同时覆盖普通
`mapper` 子目录和 fencing 等领域子目录。

注册 `DirectMyBatisSqlSessionFactoryCustomizer` Bean 可以按 `dataSourceKey` 设置插件、
TypeHandler、Configuration 或 Mapper 资源；多个定制器按 Spring 顺序执行。该扩展点在默认
DataSource/XML 设置后、工厂初始化前调用，不自动复制应用的全部 MyBatis 配置。修改 Mapper
资源时应保留组件需要的 XML；修改数据源或事务工厂导致资源不兼容时，初始化失败。

公共装配会把有序 `MyBatisAccessListener` 注入各个 Direct Access。单库、Direct 和手工创建的
`SpringMyBatisAccess` 都隔离监听器的 RuntimeException，保持原始数据库结果与异常。

## 事务资源检查

创建 Access 时校验 SqlSessionFactory Environment 与传入 DataSource 使用同一资源，并要求
`SpringManagedTransactionFactory`。`TransactionAwareDataSourceProxy` 按目标资源匹配，其他
连接池与路由代理按对象身份匹配，相同 JDBC URL 不能代替资源一致性。

`execute` 无事务时普通执行，存在实际事务时也会检查资源绑定，防止可靠任务等组件在业务库 A
事务中静默写入库 B；仅有同步绑定但仍自动提交的连接也会被拒绝。`executeInCurrentTransaction` 额外要求事务已经存在；独立提交使用
`executeInNewTransaction`。验收场景与兼容性见 [第一轮优化设计](docs/hardening-round1.md)。

## ShardingSphere-JDBC / Proxy

Relational Access 不创建 `ShardingSphereDataSource`，也不管理分片规则。

- ShardingSphere-JDBC：应用或 ShardingSphere 集成创建一个逻辑 DataSource；Access 复用它。一个逻辑
  Access 可以在 ShardingSphere 内部对应多个物理数据库。
- ShardingSphere-Proxy：应用使用普通 DataSource 连接 Proxy；物理路由发生在 Proxy 服务端。
- `sharding-sphere-jdbc.data-source-key` 与 `sharding-sphere-proxy.data-source-key` 都是应用侧
  `MyBatisAccessResolver` 的选择键，不是中间件内部的物理分片名。

详细说明见 [Relational Access 架构](docs/architecture.md#2-shardingspheredatasource-归谁管理)。

## 组件接入约束

1. Repository 依赖 `MyBatisAccess` 或 `MyBatisAccessResolver`，不直接管理 `SqlSession`。
2. Mapper XML 跟随所属组件发布，不放入 Access 模块。
3. `${table}` 只能使用已经 `MyBatisTableNameValidator` 验证的物理表名。
4. 需要与业务 SQL 同事务的操作使用 `executeInCurrentTransaction`。
5. 独立落库的抢占、失败记录等使用 `executeInNewTransaction`。

## 当前接入者

- Idempotent：`MyBatisIdempotencyRepository` + `IdempotencyMapper.xml`。
- Reliable Task：`MyBatisReliableTaskRepository` + `ReliableTaskMapper.xml`。
- Distributed Lock fencing：`MyBatisSequenceFencingTokenProvider` + `FencingTokenMapper.xml`。

各组件仍保持自己的 Repository/Provider SPI；Access 不知道幂等状态机、可靠任务 Lease 或 fencing token 语义。
