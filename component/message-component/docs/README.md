# Message Component Docs

Message Component 已经有较完整的 send / consume L0-L4 图集。本目录继续保持“图类型 -> send/consume -> L0-L4”的组织方式，不为了统一目录重新复制一套图。

## 当前模块

```text
message-api
message-spi
message-core
message-integrations
  message-integration-kafka
  message-integration-pulsar
  message-integration-rocketmq
message-starter
message-demo-springboot
```

## 统一入口

- [组件总览](diagrams/component/00-message-component-overview.puml)
- Send class：`diagrams/class/send/L0-L4`
- Consume class：`diagrams/class/consume/L0-L4`
- Send sequence：`diagrams/sequence/send/L0-L4`
- Consume sequence：`diagrams/sequence/consume/L0-L4`
- Send state：`diagrams/state/send`
- Consume state：`diagrams/state/consume`
- 枚举核对：[STATE_ENUM_AUDIT.md](diagrams/STATE_ENUM_AUDIT.md)

## 当前代码事实

### Send

`MessageTemplate -> MessageSendExecutor -> DirectMessageSender / DefaultReliableMessageSender -> MessageProvider`。

可靠发送已经接入 Retry，但 Outbox/本地消息表仍是后续能力。SendStatus.UNKNOWN 默认不应该被盲目重试，以避免不确定结果下重复发送。

### Consume V4

核心编排当前为：

```text
Provider inbound
  -> MessageConsumerAdapter
  -> decode / ConsumerDefinition resolve
  -> MessageConsumeExecutor
       -> IdempotencyStrategy
       -> TransactionStrategy
       -> MessageHandlerInvoker
  -> ConsumeDecision
  -> Provider ACK / redelivery mapping
```

重要边界：当前 message-component 已经定义 `MessageIdempotentOperations` 与 `MessageConsumeTransactionExecutor` 两个集成契约，但仓库里的 message-component **没有把 idempotent-component / transaction-component 的具体 Adapter 硬编码进 core**。

Starter 的行为是：

- 未提供真实 `MessageIdempotentOperations` 时默认 Noop；若显式开启全局幂等则 fail-fast。
- 未提供真实 `MessageConsumeTransactionExecutor` 时默认 Noop；若配置 transaction enabled + required 则 fail-fast。

因此“消费链路已经具备幂等/事务插槽”与“真实幂等/事务 Adapter 已经完成并装配”是两件事，文档不再混写。

## Provider 决策

`ConsumeDecision` 当前为：

`ACK / RETRY / DISCARD / DEAD_LETTER`。

ACK / DISCARD 对 Provider 属于确认语义；RETRY 表示保留或触发后续重投；DEAD_LETTER 当前保留统一语义，完整 DLQ 治理仍需后续实现。

## 状态图校正

当前 idempotent-component 的 `IdempotencyStatus` 已正式包含：

`PROCESSING / SUCCESS / FAILED / DISCARDED`。

因此 message consume 状态图已经把旧的 “DISCARDED 只是 MESSAGE SEMANTIC” 修正为真实的 `IdempotencyStatus.DISCARDED`。
