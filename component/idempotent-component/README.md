# idempotent-component

> 文档只描述当前实现与推荐设计，不维护历史兼容路线。

## 1. 组件定位

`idempotent-component` 用于保证：

> 同一个逻辑请求即使被重复提交，也不能产生多个合法业务效果。

当前支持两类生命周期：

- `WINDOWED`：有限时间窗口内幂等，默认适合 Redis；
- `DURABLE`：长期业务事实幂等，默认适合 JDBC。

当前正确性主线：

```text
IdempotencyRequest
    ↓
IdempotencyPolicy
    ↓
Repository atomic transition
UNIQUE / row lock / Lua / CAS
    ↓
StateMachine
EXECUTE / REPLAY / RETURN
    ↓
if EXECUTE
Business + final state
    ↑
optional local transaction
```

分布式锁不是幂等正确性的来源，只用于减少热点抢占竞争。

## 2. 当前公共 API

普通执行：

```java
IdempotencyResult<OrderResult> result = idempotencyExecutor.execute(
        IdempotencyRequest.builder()
                .key(requestId)
                .requestHash(requestHash)
                .routeKey(merchantId)
                .policyName("order-create")
                .build(),
        context -> orderService.create(command)
);
```

结果回放：

```java
IdempotencyResultPolicy<OrderResult> snapshot =
        snapshotPolicyFactory.snapshot(new IdempotencyTypeRef<OrderResult>() {});

IdempotencyResult<OrderResult> result = idempotencyExecutor.execute(
        request,
        snapshot,
        context -> orderService.create(command)
);
```

当前 Request 只负责“这次请求是谁”，策略由 `IdempotencyPolicy` / `policyName` 负责。

## 3. 当前关键模型

```text
Request
= key / requestHash / routeKey / policyName

Policy
= mode / namespace / repository / timeout / window / recovery / lock

Repository CAS/Lua
= 谁拥有当前 execution generation

ownerToken + version
= 当前 generation 的身份

StateMachine
= Repository 原子结果应该 EXECUTE / REPLAY / RETURN

ResultPolicy
= SUCCESS 后重复请求返回什么

RecoveryPolicy
= 哪些异常 generation 允许外部可靠任务接管
```

## 4. 三段事务

JDBC 且 transaction integration 可用时：

```text
Tx-A REQUIRES_NEW
  tryAcquire / tryRecover
  PROCESSING
  COMMIT

Tx-B REQUIRED
  Business
  + ResultPolicy.capture
  + markSuccess(owner, version)
  COMMIT / ROLLBACK

Tx-C REQUIRES_NEW
  markFailed(owner, version)
  COMMIT
```

Tx-A 负责让 PROCESSING 尽快可见；Tx-B 负责同库业务写与 SUCCESS 原子提交；Tx-C 负责业务失败后独立保存 FAILED。

## 5. Recovery

普通 `execute()` 遇到：

```text
PROCESSING_EXPIRED
FAILED_RETRYABLE
```

只返回状态，不自动接管。

可靠恢复由外部任务：

```text
scan candidate(owner/version)
    ↓
recover(expectedOwner, expectedVersion)
    ↓
Repository.tryRecover CAS
    ↓
RECOVERY_ACQUIRED
    ↓
复用 Tx-B / Tx-C 主执行链
```

扫描只是发现候选，真正执行权仍由 Repository 二次 CAS 决定。

## 6. ResultPolicy

当前三种策略：

| 策略 | 含义 | 推荐场景 |
|---|---|---|
| `NONE` | 只复用 SUCCESS 事实，不保存返回值 | MQ、后台任务 |
| `SNAPSHOT` | 保存第一次返回值快照并回放 | WINDOWED HTTP |
| `REFERENCE` | 保存稳定业务引用，重复请求时重新解析 | DURABLE 订单/支付/退款 |

`Replay != Retry`：Replay 不重新执行 callback。

## 7. 推荐阅读顺序

1. [`docs/code-reading-guide.md`](docs/code-reading-guide.md)
2. [`docs/architecture.md`](docs/architecture.md)
3. [`docs/core-flow.md`](docs/core-flow.md)
4. [`docs/configuration.md`](docs/configuration.md)
5. [`docs/result-replay.md`](docs/result-replay.md)
6. [`docs/recovery.md`](docs/recovery.md)
7. [`docs/transaction.md`](docs/transaction.md)
8. [`docs/package-architecture.md`](docs/package-architecture.md)
9. [`docs/status.md`](docs/status.md)

PlantUML 图位于 `docs/diagrams/`。

## 8. 当前边界

组件当前不承诺：

- 跨数据库原子提交；
- Redis + MySQL 原子事务；
- HTTP / 银行 / MQ 外部副作用自动回滚；
- 普通请求自动 Recovery；
- 内置扫描调度中心。

这些场景仍需要下游幂等、业务唯一约束、事务消息 / Outbox、补偿、对账或外部 Reliable Task。

## 执行器阅读入口

[从 DefaultIdempotencyExecutor 开始读幂等执行流程](docs/executor-reading-guide.md)：职责分包、取得执行权后的事务处理，以及路由落库的阅读顺序。
