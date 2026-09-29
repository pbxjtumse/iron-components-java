# Project Context

## 1. 项目定位

`iron-components-java` 是面向 Java / Spring Boot 分布式业务系统的可复用技术组件体系。

它不重新实现 Redis、Kafka、Pulsar、RocketMQ、OpenTelemetry、Resilience4j 或 ShardingSphere，而是在成熟基础设施之上提供：

- 统一公共 API；
- 中间件无关的核心编排；
- 可替换 Provider；
- 一致的错误、状态、事件和观测语义；
- 幂等、事务、消息、路由、锁、重试等能力的组合边界。

仓库同时包含 COLA 分层样例应用，但项目主线是 `component/`。

## 2. 技术基线

| 项目 | 当前值 |
|---|---|
| JDK | Java 17 |
| 构建 | Maven Multi Module |
| Spring Boot | 3.5.14 |
| 组件版本 | 1.0.0-SNAPSHOT |
| Maven Compiler Plugin | 3.13.0 |
| Maven Surefire / Failsafe | 3.5.2 |
| ShardingSphere | 5.5.3 |
| OpenTelemetry | 1.39.0 |
| Testcontainers | 1.20.6 |

## 3. 仓库结构

```text
iron-components-java
├── client
├── adapter
├── app
├── domain
├── infrastructure
├── start
├── component
│   ├── component-bom
│   ├── foundation-component
│   ├── concurrency-component
│   ├── retry-component
│   ├── transaction-component
│   ├── idempotent-component
│   ├── distributed-lock-component
│   ├── message-component
│   ├── cache-component
│   ├── governance-component
│   ├── observability-component
│   ├── relational-access-component
│   └── storage-routing-component
├── docs
└── scripts
```

## 4. 组件全景

下面的状态来自基线提交的代码与根 README，不能替代后续分支核验。

| 组件 | 当前代码事实 | 快照状态 |
|---|---|---|
| Foundation | core、time、id、codec、context、reflection、resource、serialization、test-support、architecture-tests | 可作为底层依赖使用 |
| Concurrency | API、配置、Core、本地/Redis Provider、TTL/治理/观测集成、Starter、Demo | 一期能力较完整 |
| Retry | API、Core、配置、Demo；提供有限、显式、可观测的进程内重试 | 核心能力已落地 |
| Transaction | API、SPI、Core、Spring Provider、Starter、JPA/MyBatis Demo | 本地事务抽象已落地 |
| Idempotent | API、Core、Redis/JDBC Provider、Transaction/Storage Routing 集成、Starter、E2E | 主链路已成型 |
| Distributed Lock | API、SPI、Core、Redis、Redisson、JDBC Fencing、Starter、Demo | 收口完善中 |
| Message | API、SPI、Core、Kafka/Pulsar/RocketMQ4、Starter、Demo | 普通收发完成，可靠发送验证中 |
| Cache | API、Core、配置、Caffeine/Redis/Composite Provider、集成、Starter、Demo | 基础能力与二期设计并行 |
| Governance | API、Model、Core、SPI、配置、Runtime、Resilience4j Engine、AOP、Starter、Demo | 建设中 |
| Observability | API、Core、OTel、Starter、Demo | Trace/MDC/OTel 已有实现，统一 Metrics 仍在建设 |
| Relational Access | API、SPI、Core、Spring Integration、Starter | 基础抽象已落地 |
| Storage Routing | API、Core、Relational、ShardingSphere-JDBC、ShardingSphere-Proxy、Starter | v1 建设中，三种路由模式已有代码 |

## 5. 核心组合关系

### 接口防重

```text
Idempotent + Transaction + Redis/JDBC Provider
```

幂等负责执行权和结果复用，事务负责业务写入与幂等状态的一致边界。

### 可靠消费

```text
Message + Idempotent + Transaction + Retry + Observability
```

Message 负责消息模型、Provider 和 ACK；Idempotent 防重复；Transaction 管理本地一致性；Retry 处理有限重试；Observability 输出事件、指标和 Trace。

### 可靠发送

```text
Message + Retry + Transaction/Outbox + Observability
```

当前 `DefaultReliableMessageSender` 已接入 `RetryExecutor`，但三种 MQ 的完整可靠发送联调仍需验证。Outbox 仍属于后续组合能力。

### 分布式互斥与防旧写

```text
Distributed Lock + Fencing Token + JDBC/Business Resource
```

锁控制当前执行权；Fencing Token 只有在最终资源执行条件写入时才能拒绝旧 Owner。

### 分库分表与同片事务

```text
Storage Routing + Relational Access + Idempotent
```

Storage Routing 解析分片和物理位置；Relational Access 执行 SQL；Idempotent 的 JDBC 记录需要与业务数据使用同一路由并进入同一事务边界。

## 6. 文档体系

- 全景入口：`README.md`。
- 文档索引：`component/DOCUMENTATION-INDEX.md`。
- 图形规范：`component/DOCUMENTATION-STANDARD.md`。
- Maven 架构：`component/MAVEN-ARCHITECTURE.md`。
- 每个组件优先读取自身 `README.md` / `readme.md` 和 `docs/README.md`。
- L0-L4 图和 State 图已经覆盖主要组件，但图中事实仍要随源码演进同步核对。
