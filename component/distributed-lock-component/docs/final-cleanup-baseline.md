# Distributed Lock Component Final Cleanup Baseline

## 本次目标

本次不是继续扩展功能，而是把分布式锁组件收口为后续消息可靠消费、任务扫描、补偿执行可复用的阶段基线。

## 已收口事项

1. `distributed-lock-spi` 作为独立 Maven module 固化。
2. `FencingTokenProvider` 及其 request/response/status 位于 SPI。
3. `MyBatisSequenceFencingTokenProvider` 位于 MyBatis fencing provider，只实现 SPI。
4. Provider 不反向依赖 Core。
5. MyBatis fencing provider 删除对 `distributed-lock-core` 的不必要依赖。
6. SPI 协议测试从 `distributed-lock-core` 移到 `distributed-lock-spi`。
7. 删除尚未上线前保留的 `LockResult.notAcquired(lockName, lockKey, waitDuration)` deprecated overload。
8. README、docs README、module structure、fencing component、class UML、sequence review 已同步当前模块边界。
9. 打包时清理 `target/`、`__MACOSX/`、`.DS_Store`。

## 最终边界

```text
api -> core -> spi
provider -> api + spi
starter -> api + core + spi + provider
demo -> starter
```

## 后续不再继续扩展的内容

当前阶段不继续扩展：

- ZK Provider
- Etcd Provider
- 公平锁
- 可重入锁
- 锁管理后台
- 锁监控大屏
- 高级续期策略

这些能力等后续真实业务需要再设计。

## 下一阶段建议

分布式锁冻结为阶段基线后，路线回到消息中间件可靠消费：

```text
message listener
  -> retry component
  -> idempotent component
  -> business consumer
  -> ACK / NACK / retry topic / DLQ
```
