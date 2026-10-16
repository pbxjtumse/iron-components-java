# Architecture Decisions

本文件记录跨组件的设计约束与理由，不代替当前能力或验证报告。修改决策时说明动机、影响模块、兼容性及必要的迁移方案；被替代的决策注明状态。

## AD-001：区分实现事实与目标设计

当前工作分支的源码、POM 和配置用于确认已实现行为；相关测试的实际执行结果用于确认验证范围。目标行为由本次明确需求与仍有效的已接受设计决定，现有代码不自动代表正确设计。

冲突处理遵循根 [AGENTS.md](../../AGENTS.md)：先判断文档过时还是实现偏离要求，再修正相应内容；历史背景只解释意图，不能代替当前实现或验收证据。

## AD-002：内部 Parent 与外部 BOM 分离

- `component/pom.xml` 管理组件源码内部依赖版本。
- `component-bom` 只面向外部业务工程发布稳定消费坐标。
- 组件源码根不再导入 `component-bom`，避免源码构建反向依赖自身发布物。

因此，不要为了让单个目录独立编译而重新给所有组件根导入 BOM。跨组件开发使用根 Reactor `-pl ... -am`。

## AD-003：API / SPI / Core / Provider / Integration / Starter 分层

- API 保持稳定和中间件无关。
- SPI 表达扩展契约。
- Core 负责编排，不直接绑定 Redis、MQ 或具体 ORM。
- Provider 负责协议与基础设施实现。
- Integration 只处理跨组件或框架桥接。
- Starter 只负责装配、条件判断和默认 Bean。

Demo 和 E2E 不进入业务公共依赖面。

## AD-004：Storage Routing 只决定去哪里

Storage Routing 输出 `dataSourceKey`、逻辑/物理表、分片键和路由模式，不负责：

- SQL 解析与改写；
- Connection 生命周期；
- 跨库归并；
- 分布式事务；
- ShardingSphere 规则维护。

## AD-005：Relational Access 只决定怎样执行

当前 Relational Access V1 以 MyBatis 为统一访问基础，由 `relational-mybatis` 与 `relational-starter` 提供 Mapper 访问、资源注册及当前 Spring 事务资源一致性检查。各组件保留自己的 Repository SPI、Mapper 与 XML；不另建通用 CRUD 或 SQL 改写层。

事务执行委托给 Spring / MyBatis 及 Transaction 集成，需要独立事务的场景使用相应 TransactionExecutor；不自行手写 Connection 的 begin/commit/rollback，不承诺跨数据库原子性。具体契约见 [Relational Access 文档](../../component/relational-access-component/README.md)。

依赖方向是 Storage Routing Integration 指向 Relational Access；Relational Access 不反向依赖 Storage Routing。

## AD-006：三种路由模式的适配边界

三种模式的职责分别为：

- Direct：返回物理 DataSource / 物理表。
- ShardingSphere-JDBC：返回逻辑 DataSource / 逻辑表。
- ShardingSphere-Proxy：返回指向 Proxy 的普通 JDBC DataSource / 逻辑表。

Adapter 不解析或改写 SQL，不复制 ShardingSphere 规则，也不自动把 `CompositeShardKey` 注入 SQL。Repository SQL 必须显式携带规则要求的分片列。代码存在不等于三种模式均已完成真实环境联调。

## AD-007：一次幂等执行最多解析一次路由

`StorageRouteAwareIdempotencyExecutor` 在一次 `execute/recover` 边界解析并设置路由作用域，下游持久化访问与业务 Repository 复用该结果。

`IdempotencyRequest` 保留 `routeKey / policyName` 等逻辑输入，不暴露物理库表字段。

## AD-008：幂等依赖原子状态迁移

正确性依赖 UNIQUE、行锁、Lua 或 CAS 等 Repository 原子操作。分布式锁只用于降低热点竞争，不能替代幂等状态机。

持久状态包括：

```text
PROCESSING / SUCCESS / FAILED / DISCARDED
```

普通 `execute()` 不自动接管过期或失败记录。Recovery 由外部可靠任务扫描候选，再通过 owner/version CAS 取得执行权。

## AD-009：关系型持久化幂等采用三段事务语义

- Tx-A `REQUIRES_NEW`：获取执行权并提交 `PROCESSING`。
- Tx-B `REQUIRED`：业务写、结果捕获和对应终态（`SUCCESS` 或 `DISCARDED`）原子提交。
- Tx-C `REQUIRES_NEW`：业务失败后独立记录 `FAILED`。

跨数据库、Redis + MySQL、外部 HTTP 或 MQ 副作用不承诺自动原子回滚。

## AD-010：Transaction 不再暴露 OWNER / PARTICIPANT 叙事

公共语义描述 Tx-A / Tx-B 以及传播行为，不把内部参与关系扩展成复杂的公共模型。

`TransactionOutcome` 当前只表达异常结果：`ROLLED_BACK / COMMIT_UNKNOWN / FAILED`；正常成功不虚构 `COMMITTED` public outcome。

## AD-011：Message Consume V4 顺序固定

约定的执行顺序如下；实际装配仍需验证，调整顺序应说明理由与兼容性影响：

```text
IdempotencyStrategy
  -> TransactionStrategy
    -> MessageHandlerInvoker
```

`MessageIdempotentOperations` 和 `MessageConsumeTransactionExecutor` 是 Integration Contract。存在扩展插槽不等于完整适配器已经完成。

## AD-012：可靠发送中的 UNKNOWN 默认不自动重试

如果 Provider 无法判断消息是否已经被 Broker 接收，盲目重试可能制造重复消息。UNKNOWN 必须由明确策略、业务幂等或对账机制处理。

## AD-013：锁丢失与 Fencing 是不同问题

Watchdog 延长租约，但不能提供绝对安全。Fencing Token 必须随写请求传给最终资源，由资源比较单调递增 Token 并拒绝旧值。

## AD-014：文档与代码同变更

修改公共 API、核心类、状态枚举、Provider 边界或模块结构时，同步检查组件 README、L0-L4 图、State 图和文档索引。

## AD-015：公共 Direct 装配与事务资源失败前置

Relational Starter 提供显式启用的 Direct 资源目录及解析器；幂等的旧 Direct 开关复用公共资源配置，
路由校验与幂等事务协调留在对应集成层，Relational Access 不反向依赖 Storage Routing 或幂等组件。

Access 构造时校验 DataSource/SqlSessionFactory Environment 配对与 Spring 事务工厂；普通执行在实际
事务存在时也检查资源绑定，错误配对和异源访问在 Mapper 执行前失败。代价是此前依赖隐式跨库
自动提交的调用需要改为明确的独立事务边界。保留原构造函数与幂等 Direct 配置；每库工厂定制和
监听器注入属于公共资源装配职责。详细契约与验收场景见
[第一轮优化设计](../../component/relational-access-component/docs/hardening-round1.md)。
