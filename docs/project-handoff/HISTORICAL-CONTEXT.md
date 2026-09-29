# Historical Conversation Context

本文件保留对后续设计有价值的历史会话背景。它不是当前代码事实；与当前分支冲突时，以代码和 `CURRENT-STATE.md` 为准。

## 1. 长期目标

项目希望形成可复用、模块化、可插拔的 Java 技术底座，并进一步服务业务模板和 AI 知识问答。

关注点不是堆积工具类，而是建立可以组合的执行边界：

```text
Foundation
  -> Concurrency / Retry
  -> Transaction / Idempotent / Lock
  -> Message / Cache / Storage Routing
  -> Governance / Observability
  -> Business Templates
```

## 2. 维护者偏好

- 中文文档，但类名、方法名和枚举必须与代码一致。
- 组件应有职责总表、组件图、L0-L4 时序图和尽量完整的状态图。
- Demo 与 Starter 分离，业务使用方式和组件内部实现分离。
- 优先保证代码、POM、文档和图的一致性，不接受“文档看起来完整但代码不存在”。
- 路由、事务、幂等和消息组合时，应明确连接复用、事务边界和失败语义。
- 验证结论必须来自实际构建、测试或联调，不能只凭代码阅读宣布通过。

## 3. 关键演进记录

历史讨论过的分支包括：

- `feature/storage-routing-v1`
- `feature/idempotent-v2-route-aware-storage`
- `storage-routing-shardingsphere-jdbc-v1`
- `feature/storage-routing-shardingsphere-proxy-v1`

在当前 master 快照中，ShardingSphere-JDBC 和 ShardingSphere-Proxy 第一版 Adapter 已经合入。新账号不应从“是否增加 Proxy Adapter”重新讨论，而应直接检查真实 E2E 和 SQL 分片列。

## 4. Storage Routing 与 Idempotency 的设计意图

历史目标是：一次幂等操作只计算一次路由，幂等技术表与业务表落到同一分片，并尽量复用事务绑定 Connection。

核心关注点：

- `CompositeShardKey` 统一单字段和复合字段分片键。
- Request 只保存逻辑路由输入，不泄露物理库表。
- Direct、ShardingSphere-JDBC、Proxy 采用相同 Storage Route 抽象。
- Adapter 不替代中间件，不隐藏 SQL 必须携带分片列的要求。
- 10×10 分库分表曾作为 Direct 模式 E2E 目标。

## 5. Transaction 与 Idempotency 的设计意图

曾重点讨论：

- PROCESSING 要先通过短事务可见；
- Business + SUCCESS 要进入同一业务事务；
- FAILED 要在业务回滚后独立保存；
- 同一事务内应复用连接，而不是每层重新取得物理连接；
- Recovery 不能只依赖扫描结果，必须通过 owner/version 再次 CAS。

这些意图已经部分落入三段事务和 Storage Route-aware 代码，但仍需要真实数据库 E2E 证明。

## 6. Message 的设计意图

消息组件目标是统一 Kafka、Pulsar 和 RocketMQ4 的发送与消费模型，并逐步组合 Retry、Idempotent、Transaction 和 Observability。

历史推进顺序：

1. 三种 MQ 普通收发。
2. 发送可靠性和异常分类。
3. 消费链路 V4：幂等策略、事务策略、Handler 调用。
4. Outbox、可靠恢复和运营级观测。

当前 master 已完成第 1 步，发送可靠性已有代码但仍需联调；消费 Integration Contract 已存在，完整生产闭环仍不能假定完成。

## 7. Distributed Lock 的设计意图

历史重点包括 Redis token + Lua 解锁/续租、Watchdog、事件、指标、执行模板，以及 JDBC sequence fencing token。

必须持续强调：

- 锁续期不等于绝对所有权安全；
- fencing token 只有在业务资源比较 token 时有效；
- Redis、Redisson 和 JDBC Fencing 的能力差异要通过 Provider Capabilities 表达。

## 8. 尚未关闭的历史诉求

- 彻底核验并尽量移除不需要的 H2 Demo / 测试依赖。
- 修复或重写 POM 校验脚本，使其符合 Parent 与 BOM 分离的新架构。
- 完成全仓 CI 构建，而不是仅验证单组件。
- 完成 ShardingSphere-JDBC / Proxy 的真实环境验证。
- 完成 Message 三 MQ 可靠发送联调和 Provider 异常映射核验。
- 继续收口 Governance、Observability、Cache 的实现与文档差距。
- 后续可使用真实 OpenTelemetry Trace 驱动链路动画，但这属于可观测与文档体验层，不应影响核心正确性。
