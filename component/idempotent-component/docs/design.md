# Idempotent Component V1.3 Design

## 1. 三层状态模型

### 持久状态

只保存：

```text
PROCESSING
SUCCESS
FAILED
```

### Repository 原子判定状态

普通抢占：

```text
ACQUIRED
SUCCESS
PROCESSING_ACTIVE
PROCESSING_EXPIRED
FAILED_RETRYABLE
FAILED_FINAL
KEY_CONFLICT
PROVIDER_ERROR
```

恢复抢占：

```text
RECOVERY_ACQUIRED
SUCCESS
PROCESSING_ACTIVE
NOT_RECOVERABLE
FAILED_FINAL
NOT_FOUND
STALE_CANDIDATE
KEY_CONFLICT
PROVIDER_ERROR
```

### Executor 最终结果

```text
EXECUTED / RECOVERED / REPLAYED
PROCESSING / PROCESSING_EXPIRED
PREVIOUS_FAILED_RETRYABLE / PREVIOUS_FAILED_FINAL
RECOVERY_NOT_ALLOWED / STALE_RECOVERY_CANDIDATE
KEY_CONFLICT / OWNERSHIP_LOST
RESULT_POLICY_*
TRANSACTION_*
REPOSITORY_ERROR / LOCK_*
```

不要把数据库状态、CAS 判定、一次 API 调用结果塞进同一个 enum。

---

## 2. Policy 模型

```text
IdempotencyRequest
  = 本次请求是谁

IdempotencyPolicy
  = 这类请求怎么执行

IdempotencyResultPolicy<T>
  = SUCCESS 以后重复请求返回什么

IdempotencyRecoveryPolicy
  = 哪些异常 generation 允许可靠任务接管
```

---

## 3. 正确性顺序

```text
optional short lock
      ↓
Repository atomic transition
      ↓
StateMachine interpretation
      ↓
EXECUTE / REPLAY / RETURN
      ↓
if EXECUTE:
Business + final state
      ↑
optional local transaction
```

Repository CAS/Lua 永远在 StateMachine 之前完成。

---

## 4. 普通执行不自动恢复

`execute()` 发现：

```text
PROCESSING_EXPIRED
FAILED_RETRYABLE
```

只返回状态。

显式 `recover()` 才能按 RecoveryPolicy 接管。

---

## 5. Recovery

```text
Reliable Task scan
  ↓
candidate(owner/version)
  ↓
recover(expectedOwner/version)
  ↓
Repository atomic re-check
  ↓
new owner + version+1
```

旧候选返回 `STALE_CANDIDATE`。

---

## 6. Result replay

重复请求命中 SUCCESS：

```text
NONE      -> REPLAYED + no value
SNAPSHOT  -> replay first success response snapshot
REFERENCE -> resolve stable business reference
```

不会重新执行 callback。

---

## 7. Transaction

JDBC transaction-aware 模式：

```text
Tx-A REQUIRES_NEW: PROCESSING
Tx-B REQUIRED: Business + ResultPolicy.capture + SUCCESS
Tx-C REQUIRES_NEW: FAILED
```

详细见 `transaction-boundary.md`。

---

## 8. WINDOWED 与 DURABLE

```text
WINDOWED
  finite semantic idempotency window
  Redis default
  can restart generation after window end

DURABLE
  long-lived business fact
  JDBC default
  external reliable recovery enabled by default
```

---

## 9. routeKey

推荐：

```text
routeKey       = merchantId / userId 等业务路由元数据（不直接决定物理分片）
idempotencyKey = operation + requestId
requestHash    = canonical business fingerprint
```

相同 key 跨 route/hash 使用必须返回冲突。真正的物理分片由外层 StorageRouteContext 或默认的 idempotencyKey 路由决定。
