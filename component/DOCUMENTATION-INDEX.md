# Iron Components Documentation Index

> 本索引对应 `docs/component-docs-l0-l4-v1` 文档整理基线。所有图优先以当前代码中的模块、类、枚举和执行边界为依据。

## 统一规范

- [文档与 UML 分层规范](DOCUMENTATION-STANDARD.md)
- L0：组件外部总览
- L1：Public API 主流程
- L2：重要异常/分支场景
- L3：Core 内部协作
- L4：Provider / Spring / Middleware 集成
- State：真实枚举优先；文档推导状态必须标 `[DOC MODEL]`

## 组件文档入口

| 组件 | 文档入口 | Component | Sequence L0-L4 | State |
|---|---|---:|---:|---:|
| Foundation | [docs](foundation-component/docs/README.md) | ✅ | ✅ | ✅（仅有状态语义的基础对象） |
| Concurrency | [docs](concurrency-component/docs/README.md) | ✅ | ✅ | ✅ AsyncTaskStatus |
| Retry | [docs](retry-component/docs/README.md) | ✅ | ✅ | ✅ RetryStatus + DOC lifecycle |
| Transaction | [docs](transaction-component/docs/README.md) | ✅ | ✅ | ✅ TransactionStage / Outcome |
| Idempotent | [docs](idempotent-component/docs/README.md) | ✅ | ✅ | ✅ 多层状态图 |
| Distributed Lock | [docs](distributed-lock-component/docs/README.md) | ✅ L0-L3 | ✅ L0-L4 | ✅ 多张生命周期/映射图 |
| Message | [docs](message-component/docs/README.md) | ✅ | ✅ Send + Consume L0-L4 | ✅ Send + Consume |
| Cache | [docs](cache-component/docs/README.md) | ✅ | ✅ | ✅ result/lifecycle |
| Governance | [docs](governance-component/docs/README.md) | ✅ | ✅ | ✅ invocation lifecycle |
| Observability | [docs](observability-component/docs/README.md) | ✅ | ✅ | ✅ span lifecycle |
| Relational Access | [docs](relational-access-component/docs/README.md) | ✅ | ✅ | ✅ SQL + Connection |
| Storage Routing | [docs](storage-routing-component/docs/README.md) | ✅ | ✅ | ✅ route scope lifecycle |

`component-bom` 是 build/version management artifact，没有运行时状态机，按规范豁免 Sequence/State Diagram。

## 本轮代码-文档一致性重点修正

### Idempotent

- 当前 route-aware 装饰器名称是 `StorageRouteAwareIdempotencyExecutor`。
- `IdempotencyRequest` 不再携带物理 shard 字段。
- 当前持久状态为 `PROCESSING / SUCCESS / FAILED / DISCARDED`。
- Storage Routing 集成文档按“一次 execute/recover 最多解析一次 route”重写。

### Message

- 消费 V4 当前真实结构为 `IdempotencyStrategy -> TransactionStrategy -> MessageHandlerInvoker`。
- `MessageIdempotentOperations` 与 `MessageConsumeTransactionExecutor` 是 integration contract；不能把“插槽已存在”写成“真实 Adapter 已完成”。
- Consume state 图中的 DISCARDED 已改为真实 `IdempotencyStatus.DISCARDED`。
- 旧“发送可靠性 75%-80%、消费可靠性未开始”的进度文档已改为代码事实描述。

### Storage Routing

- 当前已存在 Direct、ShardingSphere-JDBC、ShardingSphere-Proxy 三种模式。
- JDBC / Proxy Adapter 已经是代码事实，不再作为“未来才新增”的能力。
- 在线扩容/迁移仍是后续工作，和 Adapter 是否存在明确区分。

### Observability

- 当前已经存在 `ObservabilityWebFilter` 与 `TraceAspect`。
- 当前成熟主线是 Trace/MDC/OTel；统一 Metrics API 仍不能写成已完成。

### Relational Access

- “未来 Sharding Component”已经更新为当前真实的 `storage-routing-component`。
- Relational Core 不计算 shard，也不 begin/commit/rollback。

### Transaction

- `TransactionOutcome` 当前只表达异常结果：`ROLLED_BACK / COMMIT_UNKNOWN / FAILED`。
- 正常成功不虚构 `COMMITTED` public outcome。

## 维护要求

修改组件核心类、公开枚举、Provider 边界或模块结构后，应同步检查：

1. 对应 `docs/README.md`。
2. L0-L4 图中的类名/方法名。
3. State 图中的枚举名称。
4. README 中“当前能力/后续能力”的边界。
5. 本索引覆盖情况。

文档与代码冲突时，以当前代码为准，并在同一个 PR 中修正文档。
