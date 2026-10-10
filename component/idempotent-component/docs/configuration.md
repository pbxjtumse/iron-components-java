# 配置说明

## 1. 推荐配置

```yaml
xjtu:
  iron:
    idempotent:
      enabled: true

      default-policy: durable-default
      default-windowed-repository: redis
      default-durable-repository: mybatis
      processing-timeout: 30s

      windowed:
        idempotency-window: 10m
        window-policy: FIXED_FROM_FIRST_ACQUIRE
        record-retention-ttl: 0s
        recovery-mode: NONE
        recover-processing-timeout: false
        recover-failed: false

      durable:
        recovery-mode: EXTERNAL_TASK
        recover-processing-timeout: true
        recover-failed: true

      lock:
        enabled: false
        wait-time: 0s
        lease-time: 5s
        fallback-to-state-on-failure: true

      transaction:
        enabled: true
        require-template: false

      redis:
        enabled: true
        key-prefix: iron:idempotency

      mybatis:
        enabled: true
        table-name: iron_idempotency_record

      policies:
        api-submit:
          mode: WINDOWED
          namespace: api-submit
          repository-name: redis
          processing-timeout: 10s
          idempotency-window: 5m
          window-policy: FIXED_FROM_FIRST_ACQUIRE
          record-retention-ttl: 1h
          recovery-mode: NONE
          lock-enabled: true
          lock-wait-time: 0s
          lock-lease-time: 3s

        order-create:
          mode: DURABLE
          namespace: order-create
          repository-name: mybatis
          processing-timeout: 30s
          recovery-mode: EXTERNAL_TASK
          recover-processing-timeout: true
          recover-failed: true
          lock-enabled: true
```

## 2. Policy 解析顺序

当前只保留：

```text
inline IdempotencyPolicy
    >
policyName
    >
default-policy
```


推荐业务调用：

```java
IdempotencyRequest.builder()
        .key(requestId)
        .requestHash(requestHash)
        .routeKey(merchantId)
        .policyName("order-create")
        .build();
```

## 3. WINDOWED

### processing-timeout

当前 generation 的执行权租约。

### idempotency-window

相同 key 在多长时间内仍属于同一个逻辑请求。应大于 `processingTimeout`。

### window-policy

`FIXED_FROM_FIRST_ACQUIRE`：从第一次 acquire 开始固定窗口。

`SLIDING_ON_ACCESS`：窗口有效期间按有效访问推进，热点场景慎用。

### record-retention-ttl

语义窗口结束后旧物理记录额外保留多久。Retention 不应继续阻止新 generation。

## 4. DURABLE

DURABLE 没有有限幂等语义 TTL。默认使用 MyBatis，并推荐：

```text
recovery-mode = EXTERNAL_TASK
recover-processing-timeout = true
recover-failed = true
```

注意 `recover-failed=true` 只允许恢复 `failureRetryable=true` 的 FAILED。

## 5. Lock

Lock 只包：

```text
Repository.tryAcquire / tryRecover
```

不包 Business。

推荐：

```text
wait-time = 0
lease-time = 数秒
fallback-to-state-on-failure = true
```

## 6. Transaction

```yaml
transaction:
  enabled: true
  require-template: true
```

当 `MyBatisAccess` 与 `TransactionExecutor` 使用同一 DataSource 时启用 Tx-A / Tx-B / Tx-C。

支付、结算、订单等明确要求“Business + SUCCESS 同本地事务”的应用建议 `require-template=true`，避免 transaction-component 缺失时静默降级。

## 7. ResultPolicy 不放 YAML

ResultPolicy 带具体泛型和业务解析逻辑，因此在调用处显式选择：

```java
execute(request, callback) // NONE
```

```java
execute(request,
        snapshotPolicyFactory.snapshot(new IdempotencyTypeRef<OrderResult>() {}),
        callback);
```

```java
execute(request,
        IdempotencyResultPolicies.reference(...),
        callback);
```
