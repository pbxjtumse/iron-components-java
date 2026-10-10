# Distributed Lock 指标说明

Spring Boot 环境存在 Micrometer 时，Starter 自动装配 `MicrometerLockMetricsRecorder`。

| 指标名 | 类型 | 含义 |
|---|---|---|
| `iron.lock.acquire` | Timer | 加锁等待耗时及成功/失败 |
| `iron.lock.hold` | Timer | 从获取锁到结果收敛的持锁耗时 |
| `iron.lock.renew` | Counter | watchdog 续期结果 |
| `iron.lock.release` | Counter | 释放结果 |
| `iron.lock.lost` | Counter | 明确失锁次数 |
| `iron.lock.fencing` | Timer | fencing token 发号耗时和结果 |

## fencing 指标标签

| tag | 含义 |
|---|---|
| `lock.provider` | 互斥锁 Provider，例如 `redis` |
| `fencing.provider` | token 来源，例如 `redis`、`mybatis-sequence` |
| `namespace` | 锁命名空间 |
| `success` | 发号是否成功 |

其他指标常用标签包括 `provider`、`namespace`、`lock`、`status`、`success`。

`lock` 必须使用归一化 pattern，不能直接使用订单号、用户号等完整业务 ID，避免 Prometheus 高基数。

## 建议告警

- `iron.lock.fencing{success="false"}` 在短窗口持续增长：检查 JDBC/Redis 可用性。
- `iron.lock.lost` 增长：检查 leaseTime、GC、网络抖动和业务执行耗时。
- `iron.lock.release{success="false"}` 增长：检查 Redis 超时和 ownerToken 不匹配。
- acquire 等待 P95/P99 增长：检查锁粒度、热点 key 和 waitTime 配置。
