# Idempotent State Diagram

当前保留两张状态图，但职责不同：

```text
00-idempotency-full-state.puml
= 唯一权威状态模型
= 大而全，覆盖 persisted / derived / repo result / call result / recovery / windowed / operations / storage routing

01-idempotency-runtime-state-visual.puml
= 快速阅读图
= 用颜色和更少的节点突出“线上运行时到底处于什么状态、为什么会跳到下一步”
```

`00-idempotency-full-state.puml` 仍然是 `idempotent-component v2 — Shard-Ready Storage` 的唯一权威状态图。第二张图只做视觉投影，不引入新的代码状态，也不能单独修改业务语义。

阅读时必须区分五类概念：

```text
[PERSISTED]   IdempotencyStatus：真正写入 JDBC / Redis 的状态
[DERIVED]     由持久记录、时间和 failureRetryable 计算出的判定状态
[REPO RESULT] Repository.tryAcquire / tryRecover / markXxx 的原子操作结果
[CALL RESULT] IdempotencyExecutor / IdempotencyOperations 返回给调用方的一次调用结果
[DOC ONLY]    仅用于解释生命周期的概念节点
```

V2 真正的持久状态只有：

```text
PROCESSING
SUCCESS
FAILED
DISCARDED
```

`PROCESSING_EXPIRED`、`FAILED_RETRYABLE`、`REPLAYED`、`STALE_CANDIDATE` 等都不是数据库 `status`。

完整图同时覆盖：普通 `execute()`、owner/version CAS、Tx-B/Tx-C 完成语义、Reliable Recovery 二次 CAS、WINDOWED generation rollover、`DISCARDED`、`storeName/scanBucket`、外层 StorageRoute 以及低层 `IdempotencyOperations` 状态投影。

视觉图重点突出：

```text
蓝色   PROCESSING / 执行中
绿色   SUCCESS / 成功终态
橙色   FAILED / 失败终态
灰色   DISCARDED / 丢弃终态
黄色   派生判定状态
紫色   Recovery 接管流程
```

如果两张图出现语义不一致，以 `00-idempotency-full-state.puml` 和当前代码为准。
