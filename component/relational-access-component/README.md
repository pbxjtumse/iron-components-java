# Relational Access Component

Relational Access V1 是所有关系型技术表的 MyBatis 公共接入层。它不是 ORM，也不再提供通用 SQL/RowMapper 模板。

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
| `relational-starter` | 单 DataSource 下自动复用 MyBatis/Spring 资源，并为缺失的 `TransactionExecutor` 创建本地事务适配 |

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

`DirectMyBatisResourceRegistry` 对每个 DataSource 同时创建：

- 一个 `SqlSessionTemplate`；
- 一个与该 DataSource 绑定的 `TransactionExecutor`；
- 一个 `MyBatisAccess`。

这三者来自同一份资源目录，防止“路由选中库 A，事务却由库 B 管理”。当前线程已有其他 DataSource 的事务时，目录会拒绝跨库冒充本地事务。

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
