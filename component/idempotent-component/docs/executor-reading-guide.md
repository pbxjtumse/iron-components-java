# 从 DefaultIdempotencyExecutor 开始读幂等执行流程

这次重构按职责拆分实现，保留 IdempotencyExecutor 接口和 DefaultIdempotencyExecutor 的原构造入口。业务继续调用 execute(request, resultPolicy, callback)，不需要装配内部辅助类，也不需要实现新的流程模板接口。

## 第一遍只读入口

在 `idempotent-core` 的 `execution/DefaultIdempotencyExecutor` 中，沿着 execute 阅读五步：

1. `preparer.prepareExecution` 校验请求、解析策略、选择 Repository、检查结果存储能力，得到一次调用的执行配置。
2. 构造 `IdempotencyAcquireRequest`，通过 `stateOperations.invoke` 调用 Repository.tryAcquire，原子判断能否取得执行权。
3. `stateMachine.onAcquire` 将数据库返回的状态翻译成执行、重放或直接返回的决定。
4. `applyAcquireDecision` 根据决定分流；它自身不执行 SQL。
5. 只有 EXECUTE 才进入 `businessExecutor.executeAcquired`；REPLAY 交给 resultHandler，其余情况直接构造响应。

`recover` 是独立恢复入口。普通 execute 不会因为 PROCESSING 超时或 FAILED 可重试就自动接管；recover 仍需策略允许，并携带预期 owner/version 检查。

## 包与职责

| 包 / 类 | 应关注什么 |
| --- | --- |
| execution.DefaultIdempotencyExecutor | 正常请求和恢复请求的编排、决策分流 |
| execution.preparation.IdempotencyExecutionPreparer | 输入校验、策略与 Repository 解析 |
| execution.preparation.IdempotencyExecutionDefinition | 本次调用的策略、Repository、结果策略 |
| execution.lock.IdempotencyStateOperationExecutor | 抢占/恢复操作的可选短锁与降级 |
| execution.lock.StateOperationOutcome | 状态调用返回值、锁降级标记、锁拒绝及原因 |
| execution.business.IdempotencyBusinessExecutor | 已取得执行权后的业务与完成处理 |
| result.IdempotencyResultHandler | 结果捕获、封装与重复请求重放 |

这些是 Core 内部实现分工。public 可见性用于跨子包协作，不代表业务必须直接使用它们。StateOperationOutcome 也不是数据库状态机，更不代表已经取得业务执行权；是否取得执行权仍由 Repository 返回的 acquire/recovery 状态决定。

## 第二遍读取得执行权后的处理

原来的 `executeOwned` 现在对应 `IdempotencyBusinessExecutor.executeAcquired`。它用已抢占记录的 ownerToken/version 构造业务上下文，再判断是否具备事务协调器以及 Repository 是否支持业务事务参与。

启用事务时，`executeOwnedTransactionally` 保留一个完整的事务工作块：业务 callback → 捕获结果 → owner/version 条件更新 SUCCESS。SUCCESS 未更新成功时抛出完成拒绝异常，使事务协调器回滚该事务工作。不能将这三步拆成三个独立提交的阶段。

业务异常或结果捕获失败，需要在事务协调器处理异常后再记录失败。提交结果未知时返回 TRANSACTION_COMMIT_UNKNOWN，不贸然写 FAILED，因为数据库可能已经提交成功。没有业务事务参与时，业务执行与 SUCCESS 更新之间仍有崩溃窗口，重构没有消除这一限制。

Tx-A/Tx-C 的独立状态事务由具体 Repository 的 JDBC 执行层负责；Core 不直接创建 Connection。Tx-B 使用 REQUIRED：若加入外层事务，最终提交/回滚时机归外层事务所有，不能将 execute 返回等同于外层事务已提交。

## 第三遍再接路由和数据库

外层 `StorageRouteAwareIdempotencyExecutor` 先复用业务绑定路由，或者创建一次路由作用域，然后调用本执行器。Core 依赖 IdempotencyRepository 接口，不依赖 CompositeShardKey 或物理 DataSource。

`RoutedJdbcIdempotencyRepository` 从绑定路由确定目标并执行相应状态操作；SQL/连接选择与 Spring 事务集成仍由原有 Provider、bridge、RelationalTemplate 和事务集成层承担。本次没有改变路由算法、连接选择、表结构或 owner/version 条件。

## AcquireRequest 的构造方式

`IdempotencyAcquireRequest.builder()` 用命名字段替代入口中 13 个位置参数。原构造器保留，现有调用方不必同步修改。Builder 不隐式生成 owner、时间或策略默认值；这些仍由 Core 明确传入。业务调用者通常只构造 IdempotencyRequest，不直接创建 Repository 的 AcquireRequest。

## 回归验证

新增核心测试覆盖事务工作中的执行顺序、完成被拒绝时的回滚路径、结果捕获失败先退出事务再记失败、提交未知不记失败、非 ACQUIRED 不进入业务，以及缺失锁客户端时允许/禁止降级的行为。核心测试使用记录调用顺序的协调器，不能替代真实数据库回滚验证；分支的 Direct E2E 工作流继续运行真实 MySQL 集成测试。
