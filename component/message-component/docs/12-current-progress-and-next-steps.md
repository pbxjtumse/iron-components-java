# 12 当前进度与下一步

> 本文以当前 master 代码为准，不再沿用早期“发送可靠性约 75%~80%、消费可靠性尚未开始”的旧进度描述。

## 1. 当前已经存在的代码能力

### 普通发送 / 消费

Kafka、Pulsar、RocketMQ4 都已有 Provider 实现，消息组件拥有统一 API / SPI / Core / Starter 分层。

### 可靠发送

发送侧已经存在：

- `MessageSendExecutor`
- `DefaultReliableMessageSender`
- retry-component 集成
- `SendReliabilityInfo`
- Provider failure mapping
- Send L0-L4 时序与状态图

Outbox / Local Message Table 不在当前 message-core 中，仍属于下一阶段最终一致性能力。

### 消费 V4

消费侧当前已经存在：

- `MessageConsumerAdapter`
- `MessageConsumeExecutor`
- `IdempotencyStrategy`
- `DefaultMessageIdempotencyExecutor`
- `TransactionStrategy`
- `MessageHandlerInvoker`
- `ConsumeDecision.ACK / RETRY / DISCARD / DEAD_LETTER`
- Kafka / Pulsar / RocketMQ Provider 原生确认映射

当前主流程：

```text
Provider inbound
  -> resolve/decode
  -> IdempotencyStrategy
  -> TransactionStrategy
  -> Handler
  -> ConsumeDecision
  -> Provider ACK/redelivery
```

## 2. 需要特别区分“框架插槽”和“真实组件 Adapter”

当前 message-core 定义了两个窄集成契约：

```text
MessageIdempotentOperations
MessageConsumeTransactionExecutor
```

`MessageConsumeAutoConfiguration` 会根据这些 Bean 决定启用真实幂等/事务还是 Noop/fail-fast。

截至当前 master：

- 消费幂等编排和状态管理代码已经存在；
- 消费事务策略和 rollback decision 代码已经存在；
- 但 message-component 内没有把 idempotent-component / transaction-component 的具体实现硬编码进 core；
- 下一步若要宣布“Message + Idempotent + Transaction 完整打通”，需要补真实 Adapter Bean，并用集成测试证明共同提交/回滚和重复消息语义。

## 3. 下一步

1. 增加 message -> idempotent 的正式 integration Adapter，实现 `MessageIdempotentOperations`。
2. 增加 message -> transaction 的正式 integration Adapter，实现 `MessageConsumeTransactionExecutor`。
3. 真实验证 ACK、RETRY、DISCARD 与事务 commit/rollback 的组合。
4. Kafka / Pulsar / RocketMQ4 对相同 `ConsumeDecision` 的 Provider 行为做一致性测试。
5. 完成 DEAD_LETTER 的实际治理路径，而不只是保留枚举语义。
6. 发送侧进入 Outbox / Local Message Table 设计，解决进程重启后的可靠恢复。
7. 接入 Message Metrics / Trace。

## 4. 当前不应该误解的事情

- Retry 解决短周期失败重试，不等于 Outbox。
- IdempotencyStrategy 存在，不等于真实 Idempotent Adapter 已经装配。
- TransactionStrategy 存在，不等于所有 Handler 已经运行在真实 Spring Transaction 中。
- DEAD_LETTER 枚举存在，不等于 DLQ 全链路已经实现。
- Provider 能 ACK/NACK，不等于可靠消费已经完成生产级验证。
