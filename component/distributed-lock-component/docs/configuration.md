# Distributed Lock 配置说明

## 1. 配置分层

| 配置前缀 | 归属 | 说明 |
|---|---|---|
| `spring.data.redis.*` | 最终应用 | Redis 地址、端口、密码、database、连接与拓扑信息 |
| `spring.datasource.*` | 最终应用 | MyBatis fencing 与业务数据库连接 |
| `xjtu.iron.distributed-lock.*` | 分布式锁 Starter | 默认锁选项、Provider、等待和 fencing 选择 |
| `xjtu.iron.distributed-lock.redis.*` | 自研 Redis Lua Provider | key 前缀与 Lua Provider 语义 |
| `xjtu.iron.distributed-lock.redisson.*` | Redisson Provider | Redisson Provider、watchdog、副本同步与 client 选择 |
| `xjtu.iron.distributed-lock.fencing.mybatis.*` | MyBatis fencing Provider | token 表、重试、建表策略 |

Starter、Core、Provider 不携带业务 `application.yml`；独立 Demo 和最终 `start` 应用负责提供配置值。

Redisson Provider 默认复用 `spring.data.redis.*` 中的连接信息创建自己的 `RedissonClient`。这里的“复用”指共享连接参数，不代表 Lettuce 与 Redisson 共用同一物理连接池。如果业务应用已经提供 `RedissonClient` Bean，也可以通过 `client-bean-name` 显式选择。

## 2. 完整示例

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}
      database: ${REDIS_DATABASE:0}
      username: ${REDIS_USERNAME:}
      password: ${REDIS_PASSWORD:}
      connect-timeout: ${REDIS_CONNECT_TIMEOUT:3s}
      timeout: ${REDIS_TIMEOUT:3s}

  datasource:
    url: ${MYSQL_URL:jdbc:mysql://127.0.0.1:3306/test}
    username: ${MYSQL_USERNAME:root}
    password: ${MYSQL_PASSWORD:root}
    driver-class-name: com.mysql.cj.jdbc.Driver

xjtu:
  iron:
    distributed-lock:
      enabled: true

      # redis / redisson
      default-provider: redis

      namespace: default
      lease-time: 30s
      wait-time: 0s

      # NO_WAIT / BACKOFF / PROVIDER_NATIVE
      # wait-time=0 时通常使用 NO_WAIT；Redisson 原生等待可使用 PROVIDER_NATIVE。
      # wait-strategy: NO_WAIT

      auto-renew: false

      # 为空时按 lease-time / 3 推导。
      # 对 CORE_MANAGED Provider 表示真正的续期间隔；
      # 对 PROVIDER_MANAGED Provider（Redisson）用于 Core 锁丢失检查与 max-renew-time 治理。
      # renew-interval: 10s

      max-renew-time: 10m
      fencing-required: false

      # redis：强制使用自研 Redis Provider 原生 INCR fencing
      # redisson：强制使用 Redisson RFencedLock 原生 fencing
      # mybatis-sequence：强制使用独立 MyBatis fencing Provider
      fencing-token-provider-name:

      fail-on-lock-lost: true

      # ------------------------------
      # 自研 Spring Data Redis + Lua
      # ------------------------------
      redis:
        enabled: true
        key-prefix: iron:lock
        release-channel-prefix: iron:lock:release
        fencing-key-suffix: fence

      # ------------------------------
      # Redisson Provider
      # ------------------------------
      redisson:
        enabled: false
        key-prefix: iron:lock:redisson

        # auto-renew=true 时由 Redisson provider-managed watchdog 维护 TTL。
        watchdog-timeout: 30s

        # 获取锁后检查 Redis 副本同步情况。
        check-lock-synced-slaves: true
        slaves-sync-timeout: 1s

        # 容器中没有 RedissonClient 时，根据 spring.data.redis.* 自动创建。
        create-client-if-missing: true

        # 应用存在多个 RedissonClient 时必须显式选择。
        # client-bean-name: businessRedissonClient

      # ------------------------------
      # JDBC external fencing
      # ------------------------------
      fencing:
        jdbc:
          enabled: false
          table-name: iron_lock_fencing_token
          max-retries: 5
          initialize-schema: false
          schema-platform: mysql
```

### Redisson Provider 示例

```yaml
xjtu:
  iron:
    distributed-lock:
      default-provider: redisson
      lease-time: 30s
      wait-time: 2s
      wait-strategy: PROVIDER_NATIVE
      auto-renew: true
      renew-interval: 10s
      max-renew-time: 10m

      redisson:
        enabled: true
        watchdog-timeout: 30s
```

第一版 Redisson Provider 在 `auto-renew=true` 时要求 `lease-time` 与 `watchdog-timeout` 一致，避免公共 `LockHandle.leaseTime()` 与 Redisson 实际 watchdog TTL 窗口不一致。后续若公共模型显式区分 `requestedLeaseTime` 与 `effectiveLeaseTime`，再考虑解除该限制。

## 3. fencing 选择规则

当 `fencing-required=false` 时，不生成 token。

当 `fencing-required=true` 时，采用下面的确定性规则，不对 external provider 做隐式猜测：

1. `fencingTokenProviderName` 显式指定并且等于当前 `LockProvider.providerName()`：要求当前 Lock Provider 支持 native fencing，否则参数非法。例如 `provider=redisson + fencingTokenProviderName=redisson` 使用 `RFencedLock`。
2. `fencingTokenProviderName` 显式指定且不同于当前 Lock Provider：按名称从 `FencingTokenProviderRegistry` 精确选择 external provider。例如 `provider=redisson + fencingTokenProviderName=mybatis-sequence`。
3. 未显式指定，并且当前 Lock Provider 支持 native fencing：直接走 native fencing。当前 `redis` 和 `redisson` 都支持这一模式。
4. 未显式指定，而当前 Lock Provider 不支持 native fencing：直接返回参数错误，要求调用方明确指定 external provider；组件不会因为注册表中“刚好只有一个 Provider”就自动猜测。

这条规则与 `FencingTokenCoordinator` 保持一致，目的在于让一致性策略成为显式配置，而不是由运行环境中的 Bean 数量隐式决定。

代码显式传入的 `LockOptions` 不会与全局默认值逐字段合并；传入后以该对象为准。

## 4. 三种 fencing 来源如何选择

| 维度 | Redis INCR | Redisson RFencedLock | JDBC sequence |
|---|---|---|---|
| Lock Provider | `redis` | `redisson` | 可与 `redis` / `redisson` 组合 |
| Fencing 模式 | `NATIVE` | `NATIVE` | `EXTERNAL` |
| 获取路径 | `acquire.lua` 内原子 `INCR` | `tryLockAndGetToken(...)` 原生取得 token | 加锁成功后独立数据库事务发号 |
| 发号后重新 check owner | 不需要额外 check | 不需要额外 check | 需要，防止 DB 发号期间 lease 已过期 |
| 延迟 | 低 | 低 | 较高 |
| 故障域 | 与 Redis 锁相同 | 与 Redisson/Redis 锁相同 | 可与业务数据库一致 |
| 数据回退风险 | Redis 数据回退可能破坏单调性 | 取决于 Redis/Redisson 数据持久性与故障恢复 | 取决于数据库持久性与备份恢复 |
| 推荐场景 | 定时任务、批处理、普通幂等业务 | 使用 Redisson Provider 且需要 native token 的场景 | 库存、资金状态、关键订单状态等希望把版本边界落到 DB 的场景 |

无论 token 来源是什么，只有业务资源真正执行类似 `WHERE last_fencing_token < ?` 的条件写，fencing 才能阻止旧 owner 覆盖新 owner。

## 5. JDBC token 表

```sql
CREATE TABLE IF NOT EXISTS iron_lock_fencing_token (
    namespace VARCHAR(128) NOT NULL,
    lock_name VARCHAR(256) NOT NULL,
    current_token BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (namespace, lock_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

生产环境建议由 Flyway/Liquibase 管理，配置：

```yaml
xjtu:
  iron:
    distributed-lock:
      fencing:
        jdbc:
          initialize-schema: false
```

Token 行不得删除或重置。清理 token 表会使后续 token 重新从较小值开始，破坏 fencing token 的单调性假设。数据库恢复同样需要保证 token 状态不能回退到已被业务资源接受的版本之前。

## 6. Provider 切换不是普通动态配置

`redis` 和 `redisson` 虽然都依赖同一个 Redis 基础设施，但它们的物理 key / object 格式、owner 语义和等待机制不同。相同逻辑 `lockName` 在两个 Provider 中默认不是同一个协调域。

因此生产环境不能在一批旧 Pod 使用 `provider=redis`、另一批新 Pod 使用 `provider=redisson` 时直接滚动切换，否则两边可能同时认为自己拿到了“同一把逻辑锁”。切换 Provider 应采用停流、drain、维护窗口或明确的蓝绿切换流程；关键资源同时使用 fencing 条件写进一步兜底。详见 `provider-migration-safety.md`。

## 7. Kubernetes 建议

- ConfigMap：Redis/MySQL 地址、端口、database、Redisson 与分布式锁非敏感参数。
- Secret：Redis/MySQL 用户名和密码。
- 组件语义可以由配置中心集中管理，但 Redis/DataSource 连接变化建议滚动重启，不依赖运行时热切换。
- `default-provider` 尤其不建议当作普通动态开关在线切换；它改变的是协调域，应按 Provider 迁移流程处理。
