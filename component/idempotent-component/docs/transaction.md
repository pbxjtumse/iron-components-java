# 事务边界：Tx-A / Tx-B / Tx-C

## 1. 为什么不是一个大事务

错误做法：

```text
BEGIN
  PROCESSING
  Business 20s
  SUCCESS
COMMIT
```

这会导致 PROCESSING 长时间不可见或被唯一键/行锁阻塞，也让数据库事务覆盖整个业务执行时间。

当前使用三段事务。

## 2. Tx-A：短事务保存 PROCESSING

```text
Tx-A REQUIRES_NEW
  tryAcquire / tryRecover
  PROCESSING
  COMMIT
```

目的：在业务执行前让当前 generation 尽快对其他节点可见。

## 3. Tx-B：Business + SUCCESS 原子提交

```text
Tx-B REQUIRED
  Business
  ResultPolicy.capture
  markSuccess(owner, version)
```

同一本地事务资源下，这三步一起提交或一起回滚。

为什么是 REQUIRED：如果调用方已经存在外层事务，幂等业务必须加入外层事务，不能提前独立提交 SUCCESS 后再让外层业务回滚。

## 4. transaction-bound SqlSession

只创建 `TransactionExecutor` 不够。

如果 Business Mapper 使用 DataSource-A，而 `markSuccess()` 通过另一个 DataSource/SqlSessionFactory 执行，
仍然是两个提交点。

`SpringMyBatisAccess` 复用 Spring 管理的 `SqlSessionTemplate`，并校验当前事务绑定了
同一 DataSource，确保 Business SQL 与幂等 final state 真正位于同一本地事务资源。

如果当前事务没有绑定幂等 MyBatis Access 使用的 DataSource，集成层会 fail-fast。

## 5. owner/version CAS 为什么还必须保留

A 获得：

```text
owner=A version=10
```

执行期间 B Recovery：

```text
owner=B version=11
```

A 恢复后 `markSuccess(A,10)` 更新 0 行。

如果此时 Tx-B 正常 COMMIT，A 前面的 Business SQL 仍可能泄漏。因此 Core 会通过内部异常强制 Tx-B rollback，再向上转换为 `OWNERSHIP_LOST`。

## 6. Tx-C：失败状态独立提交

Business 失败后 Tx-B 已 rollback。FAILED 不能继续写在已失败事务里，否则也会 rollback。

因此：

```text
Tx-C REQUIRES_NEW
  markFailed(owner, version)
  COMMIT
```

FAILED 仍受 owner/version CAS 保护。

## 7. ResultPolicy 为什么属于 Tx-B

如果 Business 已写数据库，而 SNAPSHOT 序列化失败，不能出现：

```text
Business committed
SUCCESS not stored
```

因此 transaction-aware 场景固定：

```text
Business
+ ResultPolicy.capture
+ markSuccess
```

同 Tx-B 完成。

## 8. COMMIT_UNKNOWN

如果数据库可能已经 COMMIT，但应用没有收到确认：

```text
TRANSACTION_COMMIT_UNKNOWN
```

当前策略：

```text
不 markFailed
不自动重执行业务
等待查询 / 对账 / Recovery 收敛
```

## 9. 边界

本地事务只解决同一事务资源：

```text
Business SQL + Idempotency MyBatis Repository
```

不能自动解决跨库、Redis + DB、银行 HTTP、MQ 等外部副作用。此类仍需下游幂等、Outbox / 事务消息、补偿和对账。
