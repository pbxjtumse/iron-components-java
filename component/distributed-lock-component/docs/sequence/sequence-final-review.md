# Distributed Lock Sequence UML Final Review

## Final rule

All sequence diagrams are organized by real call direction:

```text
API -> Core -> SPI -> Provider -> Resource
```

## Module ownership

| Module | Allowed participants |
|---|---|
| `distributed-lock-api` | `DistributedLockClient`, `LockCallback`, `LockOptions`, `LockHandle`, `FencingTokenGuard` |
| `distributed-lock-core` | `DefaultDistributedLockClient`, `LockExecutionTemplate`, `LockAcquisitionService`, `DefaultLockHandle`, `LockRuntimeState`, `FencingTokenCoordinator`, `FencingTokenProviderRegistry`, `LockWatchdog`, `LockMetricsFacade`, `LockEventPublisher` |
| `distributed-lock-spi` | `LockProvider`, `LockProviderRegistry`, `LockProviderCapabilities`, lock protocol request/response/status objects, `FencingTokenProvider`, `FencingTokenRequest`, `FencingTokenResponse`, `FencingTokenStatus` |
| `distributed-lock-provider-redis` | `RedisLockProvider`, Redis key/script helpers |
| `distributed-lock-provider-redisson` | `RedissonLockProvider`, Redisson ownership/watchdog integration |
| `distributed-lock-fencing-provider-mybatis` | `MyBatisSequenceFencingTokenProvider`、Mapper/XML 与 schema 脚本 |
| Resource | Redis, DB, business repository/resource |

## Boundary correction

`distributed-lock-spi` is now an independent Maven module.

`FencingTokenProvider` and its request/response/status objects belong to `distributed-lock-spi`.
`MyBatisSequenceFencingTokenProvider` belongs to `distributed-lock-fencing-provider-mybatis`.

Therefore:
- SPI diagrams may contain `FencingTokenProvider`.
- Provider diagrams must contain concrete implementations such as `RedisLockProvider`, `RedissonLockProvider`, and `MyBatisSequenceFencingTokenProvider`.
- API must not call SPI directly.
- Core is responsible for orchestration.

## File scope

This package contains all sequence diagrams under:

- `docs/sequence/L0-overview`
- `docs/sequence/L1-main-flow`
- `docs/sequence/L2-scenario-flow`
- `docs/sequence/L3-internal-flow`
