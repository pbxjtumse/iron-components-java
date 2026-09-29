# State Diagram Enum Audit

本次基于当前 message-component 真实代码枚举重新整理状态图。

## 本版状态图标注规则

状态图不再使用 `[DECISION ENUM]`、`[STATUS ENUM]`、`[FAILURE ENUM]` 这类自造标签。

代码枚举节点直接在状态框中展示真实枚举类名：

```text
ACK
ConsumeDecision.ACK

VALIDATION_ERROR
SendFailureType.VALIDATION_ERROR

PROCESSING
IdempotencyStatus.PROCESSING
```

非代码枚举节点才使用普通语义标签：

```text
LOGICAL           查询、时间、次数、配置推导，代码枚举中不存在
RUNTIME           执行过程节点，代码枚举中不存在
ACTION            repository / adapter 动作，不是状态枚举
PROVIDER ACTION   Kafka / Pulsar / RocketMQ 原生确认动作
PROVIDER CAUSE    Provider 原生返回或异常，不是统一状态枚举
MESSAGE SEMANTIC  message-component 语义，目前不是持久化枚举
```

## message-component 发送侧枚举

```text
SendStage:
VALIDATE, ENRICH, RESOLVE, SERIALIZE, SEND, CONFIRM, RETRY, COMPLETE

SendStatus:
CONFIRMED, FAILED, REJECTED, UNKNOWN

SendFailureType:
NONE, VALIDATION_ERROR, SERIALIZATION_ERROR, ROUTING_ERROR, PROVIDER_NOT_FOUND,
UNSUPPORTED_CAPABILITY, AUTHENTICATION_ERROR, AUTHORIZATION_ERROR, NETWORK_ERROR,
TIMEOUT, INTERRUPTED, BROKER_REJECTED, CLIENT_ERROR, RETRY_EXHAUSTED,
RETRY_EXECUTION_ERROR, UNKNOWN_OUTCOME, UNKNOWN_ERROR
```

注意：当前代码没有 `SendFailureType.INVALID_OPTIONS`，发送状态图统一使用 `SendFailureType.VALIDATION_ERROR` 表示参数校验失败。

## message-component 消费侧枚举

```text
ConsumeDecision:
ACK, RETRY, DISCARD, DEAD_LETTER

ConsumeFailureType:
NONE, DECODE_ERROR, CONSUMER_NOT_FOUND, HANDLER_ERROR, IDEMPOTENCY_CONFLICT,
IDEMPOTENCY_STORAGE_ERROR, TRANSACTION_ERROR, ACK_ERROR, PROVIDER_ERROR, UNKNOWN_ERROR

MessageIdempotencyFailurePolicy:
RETRY, DISCARD, DEAD_LETTER

IdempotentAcquireStatus:
ACQUIRED, DUPLICATE_SUCCESS, DUPLICATE_DISCARDED, PROCESSING, REJECTED, STORAGE_ERROR
```

## idempotent-component 当前持久状态枚举

```text
IdempotencyStatus:
PROCESSING, SUCCESS, FAILED, DISCARDED
```

注意：当前 `IdempotencyStatus` 已正式包含 `DISCARDED`。消息消费的 `markDiscarded(...)` 可映射为该持久终态，不再需要用 `SUCCESS + resultCode=DISCARDED` 模拟。

## retry-component 相关枚举

```text
RetryStatus:
SUCCESS, EXHAUSTED, NOT_RETRYABLE, TIMED_OUT, INTERRUPTED, CANCELLED, ABORTED, EXECUTION_FAILED

RetryFailureCategory:
UNKNOWN, TRANSIENT, THROTTLING, CONCURRENCY_CONFLICT, DEPENDENCY_UNAVAILABLE, RESULT_NOT_READY, NON_RETRYABLE
```

## Provider consume action

Provider 消费确认动作现在不再抽成 `KafkaConsumeDecisionMapper`、`PulsarConsumeDecisionMapper`、`RocketMqConsumeDecisionMapper` 这类薄包装说明类。

Provider 直接根据 `ProviderConsumeResult.decision().shouldAcknowledge()` 执行原生动作：

```text
ACK / DISCARD:
Kafka    -> commit next offset
Pulsar   -> acknowledge
RocketMQ -> CONSUME_SUCCESS

RETRY / DEAD_LETTER:
Kafka    -> no commit / seek current offset
Pulsar   -> negativeAcknowledge
RocketMQ -> RECONSUME_LATER
```
