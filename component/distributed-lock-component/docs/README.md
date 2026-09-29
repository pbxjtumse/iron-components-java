# Distributed Lock Component 文档与图表

当前文档与代码结构对应：

```text
distributed-lock-api
distributed-lock-spi
distributed-lock-core
distributed-lock-provider
  ├── distributed-lock-provider-redis
  ├── distributed-lock-provider-redisson
  └── distributed-lock-fencing-provider-jdbc
distributed-lock-starter
distributed-lock-demo
```

## 推荐阅读顺序

1. Component L0：`component/L0-overview/module-structure.puml`
2. Component L1-L3：架构、扩展点、内部结构
3. Sequence L0：组件总览
4. Sequence L1：execute / tryLock / watchdog / fencing 主流程
5. Sequence L2：not-acquired / lock-lost / release-failed / fencing-rejected 等异常场景
6. Sequence L3：Provider selection、Redis Lua、LockHandle renew/unlock/checkHeld
7. Sequence L4：`sequence/L4-integration-flow/starter-provider-integration.puml`
8. State：vocabulary -> lifecycle -> provider/fencing mapping

## L0-L4 含义

| 层级 | 当前图集重点 |
|---|---|
| L0 | 从业务入口看完整 Lock 调用 |
| L1 | acquire / execute / release / watchdog / fencing 正常流程 |
| L2 | 失败、锁丢失、fencing 拒绝、provider error |
| L3 | Core 内部与 Provider 协议 |
| L4 | Spring Boot Starter、Redis/Redisson/JDBC fencing、Micrometer/Health |

## 当前真实能力

- Redis Lua acquire / release / renew / check。
- ownerToken 安全释放和续租。
- Core-managed watchdog。
- Redisson native wait、provider-managed watchdog、native fencing。
- JDBC Sequence Fencing Token Provider。
- `FencingTokenCoordinator` 的 NONE / NATIVE / EXTERNAL 选择。
- Micrometer metrics、Spring events、HealthIndicator。

## 状态模型

公开执行结果使用 `LockStatus`：

`ACQUIRED / SUCCESS / NOT_ACQUIRED / EXECUTION_FAILED / LOCK_LOST / FENCING_REJECTED / RELEASE_FAILED / PROVIDER_ERROR / INVALID_OPTIONS`。

执行阶段使用 `LockStage`：

`VALIDATE / ACQUIRE / WAIT / EXECUTE / FENCING / RENEW / RELEASE / CHECK`。

已有 state 图分别覆盖 LockResult、LockHandle、Watchdog、Provider 状态映射和 Fencing 状态映射；不额外制造一个重复且更难维护的“超级状态图”。

## 核心文档

- `configuration.md`
- `metrics.md`
- `FAQ.md`
- `sequence/sequence-final-review.md`
