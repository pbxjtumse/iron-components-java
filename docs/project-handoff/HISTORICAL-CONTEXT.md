# 历史设计背景与候选方向

本文件保留早期讨论中的设计意图和候选方向，不代表当前实现状态，不自动形成开发任务。判断当前实现应核对任务分支代码；需求、设计与实现冲突时，按根 [AGENTS.md](../../AGENTS.md) 处理。

## 长期目标

形成可复用、模块化、可插拔的 Java 技术底座，逐步服务业务模板与 AI 知识问答。在成熟中间件之上建立可以组合的执行边界，而不是重新实现中间件。

## 历史设计意图

### Storage Routing 与 Idempotency

- 一次幂等操作只解析一次路由，幂等技术表与业务表尽量位于同一分片，并复用事务绑定资源。
- `CompositeShardKey` 统一单字段和复合分片键；Request 保留逻辑输入，不暴露物理库表。
- Direct、ShardingSphere-JDBC、Proxy 使用统一路由抽象；SQL 仍须显式携带中间件规则要求的分片列。
- 10×10 拓扑曾作为 Direct 模式的验证目标，不能据此推断现有 E2E 已覆盖该规模。

### Transaction 与 Idempotency

- `PROCESSING` 通过短事务先变为可见。
- 业务写入与成功终态在同一业务事务内提交。
- 业务失败后独立记录 `FAILED`，不随业务事务回滚丢失。
- 同一事务内复用绑定资源；Recovery 通过 owner/version CAS 取得执行权，不能只依赖扫描结果。

### Message

历史推进思路是：三种 MQ 普通收发、发送可靠性与异常分类、幂等 / 事务消费编排、Outbox 与可靠恢复、运营观测。扩展契约或恢复插槽存在，不等于完整生产闭环已经完成。

### Distributed Lock

区分租约、所有权与最终资源的 fencing 校验。历史关注 Redis token + Lua、两类 Watchdog、事件指标、执行模板与数据库 fencing token；不同 Provider 的能力通过契约表达，资源写入仍需拒绝过期 token。

各项能力的当前实现和验证状态，应查阅对应组件文档与代码，包括后来新增的 Reliable Task，不沿用旧 master 快照中的阶段判断。

## 候选方向

以下仅保留讨论价值，启动任务前需重新核对当前实现、必要性和优先级：

- Demo 打包：明确需要直接 `java -jar` 的 Boot 包，还是 IDE / 测试运行即可。
- 测试依赖：核对 H2 的用途与真实数据库验证边界，不以历史清理意向为由删除有效测试。
- 路由联调：Direct、ShardingSphere-JDBC 与 Proxy 的真实数据库事务、回滚、重复请求和 Recovery 验证。
- 消息闭环：Kafka、Pulsar、RocketMQ4 的成功 / 确定失败 / UNKNOWN 分类，以及消费 ACK 失败和恢复场景。
- 锁验证：Redis owner token、Core-managed / Provider-managed Watchdog、数据库 fencing 与最终资源条件写入。
- Cache：多实例失效通知、互斥加载、动态 CacheSpec、慢加载与降级指标。
- Governance：执行顺序以及 Runtime、Engine、Spring AOP 的组合验证。
- Observability：统一 Metrics 和事件标签；后续可探索真实 OpenTelemetry Trace 驱动的问题复现视图。
- 发布与消费：外部业务工程使用 BOM 的消费者验证、发布坐标、版本与变更日志，以及业务模板。
- 知识索引：将有效组件文档和代码索引用于后续 AI 知识库。

旧迁移清单中的 POM 缺陷、模块数量及“本机未安装 Maven”等信息不保留为当前基线；构建证据以当前任务实际执行结果为准。
