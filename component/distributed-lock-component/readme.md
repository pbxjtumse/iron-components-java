# Distributed Lock Component

> 当前状态：Final Cleanup Baseline
>
> 定位：面向业务系统的分布式锁基础组件，提供统一 API、可替换 Provider、watchdog 续期、fencing token、防旧 owner 写入辅助能力。

## 1. 模块结构

```text
distributed-lock-component
├── distributed-lock-api
├── distributed-lock-spi
├── distributed-lock-core
├── distributed-lock-provider
│   ├── distributed-lock-provider-redis
│   ├── distributed-lock-provider-redisson
│   └── distributed-lock-fencing-provider-mybatis
├── distributed-lock-starter
├── distributed-lock-demo
└── docs
```

## 2. 分层边界

```text
Business
  ↓
distributed-lock-api
  ↓
distributed-lock-core
  ↓
distributed-lock-spi
  ↓
distributed-lock-provider-*
  ↓
Redis / Redisson / JDBC / Business Resource
```

- `distributed-lock-api`：业务入口与公共模型，例如 `DistributedLockClient`、`LockOptions`、`LockHandle`、`LockResult`。
- `distributed-lock-core`：核心编排，例如加锁、等待、执行模板、watchdog、fencing 选择、事件和指标。
- `distributed-lock-spi`：Provider 契约与协议对象，例如 `LockProvider`、`LockProviderRegistry`、`FencingTokenProvider`、acquire/release/renew/check request/response。
- `distributed-lock-provider-*`：具体 Provider 实现，例如 Redis Lua、Redisson、MyBatis fencing provider。
- `distributed-lock-starter`：Spring Boot 自动装配，当前是 all-in-one starter。
- `distributed-lock-demo`：使用示例和装配验证。

## 3. 当前能力

### Redis Provider

- Lua acquire / release / renew / check。
- `ownerToken` 安全释放和安全续期。
- Core managed watchdog。
- Redis Cluster hash tag key 设计。

### Redisson Provider

- `providerName=redisson`。
- Provider native wait，映射 Redisson Pub/Sub waiting。
- Provider managed watchdog，使用 Redisson 内部 watchdog。
- `RFencedLock.tryLockAndGetToken(...)` 映射组件 `FencingTokenMode.NATIVE`。
- `RedissonOwnershipRegistry` 适配组件 `ownerToken` 与 Redisson `threadId` owner 语义。

### JDBC Fencing Provider

- `FencingTokenProvider` 已位于 `distributed-lock-spi`。
- `MyBatisSequenceFencingTokenProvider` 位于 `distributed-lock-fencing-provider-mybatis`。
- Core 通过 `FencingTokenCoordinator` 选择 NONE / NATIVE / EXTERNAL。
- External fencing 发号后会重新校验持锁状态，避免发号期间锁过期后旧 owner 继续执行业务。

## 4. 关键语义

分布式锁组件提供的是租约模型，不是 JVM 线程互斥模型。

每次加锁成功都会生成 `ownerToken`，用于证明当前 `LockHandle` 对锁的所有权。

`ownerToken` 可以保护释放和续期，但不能单独保护业务资源。对于核心状态写入，仍然需要结合：

```text
fencing token
DB 条件更新
业务状态机
幂等机制
```

Redis Provider 和 Redisson Provider 是不同协调域，不建议无保护滚动混跑。

## 5. 文档入口

- `docs/README.md`
- `docs/component/L0-overview/module-structure.puml`
- `docs/sequence/sequence-final-review.md`
- `docs/configuration.md`
- `docs/metrics.md`
- `docs/FAQ.md`

## 6. 本次收口点

- 独立 `distributed-lock-spi` Maven module。
- `FencingTokenProvider` 迁入 SPI。
- Provider 不再反向依赖 Core。
- 清理 MyBatis fencing provider 对 Core 的不必要依赖。
- SPI 协议测试迁入 `distributed-lock-spi`。
- 删除尚未上线前保留的 deprecated overload。
- 清理压缩包中的 `target/`、`__MACOSX/`、`.DS_Store`。
- 同步 README、component UML、class UML、sequence review。
