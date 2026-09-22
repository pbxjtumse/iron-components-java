# Iron Components Java

一套面向 Java / Spring Boot 分布式业务系统的可复用技术组件体系。

这个仓库不是为了重新实现 Redis、Kafka、Pulsar、RocketMQ、OpenTelemetry、Resilience4j、ShardingSphere 这些基础设施，而是在成熟基础设施之上沉淀一层更贴近业务系统的统一能力：

- 统一 API：业务代码优先依赖 api / starter，而不是直接绑定某个中间件 SDK。
- 统一编排：把重试、幂等、事务、锁、消息、缓存、治理、观测这些能力组合起来。
- 统一治理：错误分类、超时、重试、限流、熔断、指标、日志、链路追踪尽量形成一致语义。
- 统一演进：Provider 可替换，业务代码尽量不跟 Redis / MQ / JDBC / OTel / ShardingSphere 的细节强耦合。

当前仓库同时包含一个 COLA 分层样例应用和一组可复用技术组件。真正的主线在 `component` 目录。

---

## 技术基线

| 项目 | 当前选择 |
|---|---|
| JDK | Java 17 |
| 构建 | Maven Multi Module |
| 应用框架 | Spring Boot 3.5.x |
| 应用分层 | COLA 风格 client / adapter / app / domain / infrastructure / start |
| 组件版本 | 1.0.0-SNAPSHOT |
| 主要基础设施方向 | Redis、MySQL / PostgreSQL、Kafka、Pulsar、RocketMQ4、OpenTelemetry、Micrometer、Resilience4j、ShardingSphere-JDBC |
| 测试与验证 | JUnit、Testcontainers、ArchUnit、组件 Demo、POM 校验脚本、端到端多库多表验证 |

---

## 仓库结构

```text
iron-components-java
├── client                     # 应用外部接口与 DTO
├── adapter                    # Web / Wap / Mobile 等适配层
├── app                        # 应用服务编排
├── domain                     # 领域模型与领域服务
├── infrastructure             # MyBatis、外部网关、基础设施实现
├── start                      # Spring Boot 启动模块
├── component                  # 技术组件主目录
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
└── scripts
    ├── build-first.sh
    ├── validate-poms.py
    └── check-source-dependencies.py
```

---

## 当前组件全景

| 组件 | 当前代码模块 | 当前状态 | 主要解决的问题 |
|---|---|---|---|
| Foundation | foundation-core / time / id / codec / context / reflection / resource / serialization / test-support | 可作为底层依赖使用 | 通用工具门面、时间、ID、序列化、上下文、测试支撑 |
| Concurrency | api / config / core / provider / integrations / starter / demo | 一期能力较完整 | 线程池、异步任务、排队超时、执行超时、fallback、取消、任务状态、指标 |
| Retry | api / core / config / demo | 核心能力已落地 | 显式、有限、可观测的进程内重试 |
| Transaction | api / spi / core / provider-spring / starter / demo | 本地事务抽象已落地 | 为幂等、消息、可靠任务提供统一事务执行边界 |
| Idempotent | api / core / provider-redis / provider-jdbc / integration / starter / demo | Route-Aware 主链路已落地 | RPC、消息、任务等场景的幂等状态机、结果回放、恢复接管，以及与分库分表路由协同 |
| Distributed Lock | api / spi / core / redis / redisson / jdbc-fencing / starter / demo | 主体能力已收口，持续完善测试与文档 | 分布式互斥、Lua 安全释放/续租、Watchdog、Owner Token、Fencing Token、指标 |
| Message | api / spi / core / Kafka / Pulsar / RocketMQ4 / starter / demo | 普通收发已完成，可靠消费链路持续收口 | 多 MQ 统一消息模型、Provider 适配、可靠发送/消费、幂等与事务协同 |
| Cache | api / core / config / provider-caffeine / provider-redis / composite / integrations / starter / demo | 基础能力和二期设计并行 | Caffeine + Redis 多级缓存、缓存策略、击穿治理、失效事件 |
| Governance | api / model / core / spi / configs / runtime / engine / integration / starter / demo | 建设中 | 限流、熔断、隔离、超时、治理策略、Resilience4j 适配 |
| Observability | api / core / otel / starter / demo | 建设中 | Metrics、Trace、事件、OpenTelemetry / Micrometer 接入 |
| Relational Access | api / spi / core / integration-spring / starter | 基础抽象已落地 | 关系型数据库访问统一抽象，给路由、幂等、事务等组件复用 |
| Storage Routing | api / core / integration-relational / starter | v1 主链路已完成，正在向迁移/扩容能力演进 | 分库分表、读写分离、多租户、冷热数据、逻辑路由与物理位置解耦 |

状态说明：

| 状态 | 含义 |
|---|---|
| 可作为底层依赖使用 | 代码结构相对稳定，适合被其他组件依赖 |
| 一期能力较完整 | 核心场景已经覆盖，后续主要增强治理、观测和边界场景 |
| 主链路已落地 | API、Core、Provider / Integration 和关键执行链路已经可用 |
| 主体能力已收口 | 主要能力和边界已经稳定，后续重点是测试、文档、指标和 Provider 一致性 |
| 建设中 | 模块已建立，正在补齐稳定实现、集成和验证 |
| v1 主链路已完成 | 第一版核心抽象已经落地，但在线迁移、扩容和更多 Adapter 仍在继续建设 |

---

## 组件分层原则

大部分组件遵循相近的分层方式：

```text
xxx-api
    面向业务代码的稳定模型和接口

xxx-core
    组件内部主流程、状态机、执行编排

xxx-spi
    Provider 扩展契约，隔离基础设施实现

xxx-provider-*
    Redis / JDBC / Kafka / Pulsar / RocketMQ / Redisson 等具体实现

xxx-integration-*
    与其他技术组件或 Spring / OTel / Governance 的集成层

xxx-starter
    Spring Boot 自动装配与默认接入

xxx-demo
    本地验证和端到端演示
```

不是每个组件都完全拥有这些层，但整体方向一致。

---

## Maven 架构

`component/pom.xml` 是技术组件源码的 Aggregator、Parent 和内部 dependencyManagement。

`component/component-bom/pom.xml` 是对外发布的 BOM。业务项目通常不继承 `component/pom.xml`，而是 import `component-bom` 后按需引入 starter 或 api。

内部开发推荐从仓库根目录使用 Maven Reactor：

```bash
mvn -pl component/message-component -am clean compile
mvn -pl component/idempotent-component -am clean compile
mvn -pl component/distributed-lock-component -am clean compile
mvn -pl component/storage-routing-component -am clean compile
```

`-am` 会把目标组件依赖的其他 Reactor 模块一起构建。

外部业务工程消费方式：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.xjtu.iron</groupId>
            <artifactId>component-bom</artifactId>
            <version>1.0.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

之后按需引入：

```xml
<dependency>
    <groupId>com.xjtu.iron</groupId>
    <artifactId>idempotent-starter</artifactId>
</dependency>

<dependency>
    <groupId>com.xjtu.iron</groupId>
    <artifactId>message-starter</artifactId>
</dependency>
```

---

## 快速构建

全量校验脚本：

```bash
bash scripts/build-first.sh
```

脚本会依次执行：

1. 校验 POM 聚合、父链和 component-bom。
2. 校验源码直接使用的第三方 API 是否在模块 POM 中声明。
3. 编译全部生产代码。
4. 执行全部测试并打包。

如果只是开发某个组件，建议优先使用局部构建：

```bash
mvn -U -pl component/foundation-component -am clean verify
mvn -U -pl component/concurrency-component -am clean verify
mvn -U -pl component/message-component/message-demo-springboot -am clean package -DskipTests
mvn -U -pl component/storage-routing-component -am clean verify
```

---

# 典型组合关系

这些组件不是孤立存在的。真正的价值在于组合。

## 接口防重复提交

```text
Idempotent
    + Transaction
    + Redis / JDBC Provider
```

适合创建订单、提交申请、支付请求、营销领取、人工操作等场景。

幂等组件负责判断同一个逻辑请求是否已经处理过；事务组件负责把业务写入和幂等状态更新放在一致边界内。

---

## MQ 可靠消费

```text
Message
    + Idempotent
    + Transaction
    + Retry
    + Observability
```

Message 负责接收、ACK、Provider 映射和消费上下文。

Idempotent 负责重复消息安全。

Transaction 负责业务写入和幂等状态一致。

Retry 负责短时间、有限次数、可解释的失败重试。

Observability 负责把消费状态、异常、耗时、重试次数暴露出来。

当前消费链路已经进一步向统一编排演进：消息消费可以接入 `IdempotencyExecutor` 和本地事务模板，由统一消费流程完成幂等判断、业务执行、事务边界和最终 ACK / 重投决策。

---

## MQ 可靠发送

```text
Message
    + Retry
    + Transaction / Outbox
    + Observability
```

当前 `message-component` 普通收发已经覆盖 Kafka、Pulsar、RocketMQ4。

发送侧已经具备统一 Provider 抽象和有限重试能力；下一阶段将继续推进 Outbox / Local Message Table，使“业务事务提交”和“消息最终发送成功”之间具备可恢复的一致性保障。

---

## 分布式互斥与防旧写

```text
Distributed Lock
    + Watchdog
    + Fencing Token
    + Relational Access / JDBC
```

普通分布式锁解决“当前谁能进入”。

当前 Redis Provider 已围绕 Lua 安全释放、续租、Owner Token 和 Watchdog 完善主链路。

Fencing Token 进一步解决：

```text
旧 Owner 的锁虽然已经失效，
但旧请求仍继续执行并尝试写入
```

的问题。

通过单调递增的 Token，存储侧可以拒绝旧 generation 的写入，从而降低“锁失效但旧请求继续写”的风险。

---

## 异步并行与治理

```text
Concurrency
    + Governance
    + Observability
```

Concurrency 负责线程池、任务提交、超时、fallback、取消和任务状态。

Governance 负责限流、隔离、熔断等稳定性策略。

Observability 负责输出统一指标和链路标签。

---

## 多级缓存

```text
Cache
    + Caffeine Provider
    + Redis Provider
    + Distributed Lock
    + Governance
    + Observability
```

Cache 当前方向是 Caffeine + Redis 二级缓存。

后续重点是多实例本地缓存失效通知、分布式互斥加载、动态 CacheSpec、缓存事件模型、治理接入和慢日志指标。

---

## 分库分表与路由感知幂等

```text
Storage Routing
    + Relational Access
    + Idempotent
    + Transaction
```

Storage Routing 负责把稳定的业务分片语义解析为具体存储位置。

Idempotent 不再自己计算数据库和表位置，而是复用同一次 `StorageRoute`。

Transaction 负责保证业务 SQL 和幂等状态 SQL 在可以共址的情况下共享同一物理数据库、本地事务和连接。

典型链路：

```text
Business ShardKey
        ↓
CompositeShardKey
        ↓
StorageRouteResolver
        ↓
RouteMappingStrategy
        ↓
StorageRoute
        ↓
PhysicalStorageLocation
        ↓
Relational Access / DataSource
        ↓
Business SQL + Idempotency SQL
```

这样可以避免：

```text
业务代码一套分库算法
幂等组件一套分库算法
消息组件再维护一套路由逻辑
```

导致的数据位置不一致问题。

---

# 核心组件说明

## Foundation Component

定位：全仓库底层技术门面。

当前模块：

```text
foundation-core
foundation-time
foundation-id
foundation-codec
foundation-context
foundation-reflection
foundation-resource
foundation-serialization
foundation-test-support
foundation-architecture-tests
```

当前版本强调薄封装：优先复用 JDK 17、Apache Commons、Jackson 等成熟能力，避免重新发明大量工具类。

命名上避免 `StringUtils`、`CollectionUtils` 这类容易与开源库冲突的类名，统一使用 `IronStrings`、`IronCollections`、`IronLists`、`IronMaps`、`IronDigests` 等门面。

---

## Concurrency Component

定位：统一异步执行和线程池治理。

主要能力：

- 多线程池配置和注册。
- 异步 run / supply / submit / execute。
- Queue Timeout 和 Execution Timeout。
- fallback、取消、interrupt。
- TaskHandle、TaskExecutionRegistry、TaskExecutionListener。
- 错误结构化和任务状态管理。
- Micrometer 指标和后续治理集成。

适合远程接口并行查询、批量分片处理、后台异步任务、清结算 / 营销 / 风控类并行执行场景。

---

## Retry Component

定位：显式、有限、可观测的短周期重试。

设计原则：

- 默认不对所有异常盲目重试。
- 最大次数包含第一次执行。
- 支持异常分类、固定退避、指数退避、随机抖动。
- 支持最大总耗时。
- 中断应立即停止。
- 有副作用操作必须由调用方保证幂等。

Retry 不负责长周期任务调度。分钟级、小时级、跨重启的恢复应交给 Message Delay、Task、Outbox 或持久化恢复任务。

---

## Transaction Component

定位：统一本地事务执行抽象。

当前模块：

```text
transaction-api
transaction-spi
transaction-core
transaction-provider-spring
transaction-spring-boot-starter
transaction-demo-mybatis
transaction-demo-jpa
```

它不是 XA、TCC、Saga 或 Seata 的替代品。

当前重点是为组件内部组合提供统一本地事务边界，例如：

- 幂等状态 + 业务写入；
- 消息可靠消费；
- Outbox；
- Route-Aware Storage 场景下的同库事务与连接复用。

---

## Idempotent Component

定位：统一幂等执行状态机，并支持 Route-Aware Storage。

当前支持两类生命周期：

| 类型 | 说明 | 推荐存储 |
|---|---|---|
| WINDOWED | 有限时间窗口内幂等 | Redis |
| DURABLE | 长期业务事实幂等 | JDBC |

关键模型：

| 模型 | 含义 |
|---|---|
| IdempotencyRequest | key、requestHash、policyName 等执行上下文 |
| IdempotencyPolicy | mode、namespace、repository、timeout、window、recovery、lock |
| Repository | Redis Lua / JDBC UNIQUE / row lock / CAS |
| ownerToken + version | 当前执行 generation 身份 |
| StateMachine | EXECUTE / REPLAY / RETURN |
| ResultPolicy | NONE / SNAPSHOT / REFERENCE |
| RecoveryPolicy | 判断哪些异常状态允许可靠任务接管 |

当前 JDBC 幂等已经进一步与 Storage Routing 集成：

```text
业务输入
  ↓
自动构造 CompositeShardKey
  ↓
StorageRoutedIdempotencyExecutor
  ↓
只解析一次 StorageRoute
  ↓
业务 Repository 与 Idempotency Repository 共享路由
```

在可共址场景中，业务数据和幂等记录可以落在同一物理库，并在同一个本地事务和 JDBC Connection 中完成。

当前已经围绕 `10 库 × 10 表` 场景进行了端到端验证。

幂等不等于分布式锁。锁可以降低并发竞争，但幂等正确性应来自 Repository 的原子状态转移和唯一约束。

---

## Distributed Lock Component

定位：分布式资源互斥和 Fencing Token。

当前模块：

```text
distributed-lock-api
distributed-lock-spi
distributed-lock-core
distributed-lock-provider-redis
distributed-lock-provider-redisson
distributed-lock-fencing-provider-jdbc
distributed-lock-starter
distributed-lock-demo
```

主要能力：

- tryLock / execute。
- LockHandle unlock / renew / checkHeld / assertHeld。
- ownerToken。
- Redis Lua 原子释放。
- Redis Lua 原子续租。
- renew 和 Watchdog。
- wait strategy。
- LockStatus、LockStage。
- Redis Provider。
- Redisson Provider。
- JDBC Fencing Token Provider。
- Fencing Token Sequence。
- Metrics / Template。
- L0-L3 PlantUML 文档。

当前重点已经从“能否加锁”逐步转向：

```text
锁生命周期安全
+ Watchdog
+ Fencing
+ Provider 一致性
+ 指标
+ 完整测试
```

---

## Message Component

定位：统一消息模型、生命周期治理和多 MQ Provider 适配。

当前模块：

```text
message-api
message-spi
message-core
message-integrations
message-starter
message-demo-springboot
```

Provider 方向：

| Provider | 当前状态 |
|---|---|
| Kafka | 普通收发已通过 |
| Pulsar | 普通收发已通过 |
| RocketMQ4 | 普通收发已通过 |
| 三 Provider 同时收发 | 已通过 |
| 发送可靠性 | 已接入统一 Retry，继续向 Outbox 演进 |
| 消费可靠性 | V4 消费链路持续收口，幂等与事务已经开始进入统一执行链 |

当前发送主链路：

```text
业务代码
  -> MessageTemplate
  -> MessageEnvelopeEnricher
  -> DestinationResolver
  -> MessageWireCodec
  -> MessageSendExecutor
  -> DirectMessageSender / DefaultReliableMessageSender
  -> MessageProvider
  -> Kafka / Pulsar / RocketMQ4
```

消费侧正在从“Provider 收到消息后直接执行 Handler”演进为统一消费编排：

```text
Provider Message
    ↓
Decode / Normalize
    ↓
Consumer Resolve
    ↓
IdempotencyExecutor
    ↓
Transaction Boundary
    ↓
Business Handler
    ↓
Consume Decision
    ↓
ACK / Retry / Redelivery / DLQ
```

目标是让 Kafka、Pulsar、RocketMQ4 在上层具有一致的消费语义，而 Provider 只处理各自 SDK 的协议映射。

---

## Cache Component

定位：统一缓存访问和多级缓存治理。

当前模块：

```text
cache-api
cache-core
cache-config
cache-provider
cache-integrations
cache-starter
cache-demo
```

Provider 方向：

- Caffeine 本地缓存。
- Redis 分布式缓存。
- Composite 多级缓存。

后续重点：

- 多实例本地缓存失效通知。
- 分布式互斥加载，防多实例击穿。
- 动态 CacheSpec。
- CacheEvent 模型。
- governance-component 接入。
- 慢日志和缓存事件指标。

---

## Governance Component

定位：稳定性治理抽象。

当前模块：

```text
governance-api
governance-model
governance-core
governance-spi
governance-configs
governance-runtime
governance-engine
governance-integration
governance-observability
governance-starters
governance-demo
```

设计边界：

- governance-core 不直接绑定 Resilience4j、Sentinel、Nacos。
- governance-api 不依赖 Spring。
- 业务代码不直接依赖具体治理框架。
- 具体限流、熔断、隔离能力通过 engine / provider 适配。

---

## Observability Component

定位：统一可观测性抽象和 OpenTelemetry / Micrometer 接入。

当前模块：

```text
observability-api
observability-core
observability-otel
observability-starter
observability-demo-app
```

目标是让组件统一输出：

```text
Metrics
Trace
Event
Runtime State
```

而不是让每个组件直接散落 Micrometer / OTel / 日志细节。

后续将围绕 Prometheus + Grafana 建立组件级指标和 Dashboard。

---

## Relational Access Component

定位：关系型数据库访问基础抽象。

当前模块：

```text
relational-api
relational-spi
relational-core
relational-integration
relational-starter
```

它为 `storage-routing`、`idempotent`、`transaction` 等组件提供更统一的关系型访问语义，避免每个组件重复处理 DataSource、路由、SQL 执行上下文等问题。

---

## Storage Routing Component

定位：统一业务存储路由，为分库分表、读写分离、多租户、冷热数据以及后续在线迁移提供稳定的逻辑路由层。

当前模块：

```text
storage-routing-api
storage-routing-core
storage-routing-integration-relational
storage-routing-starter
```

核心模型包括：

```text
ShardKey
ShardValue
CompositeShardKey
StorageRoute
PhysicalStorageLocation
StorageRouteResolver
RouteMappingStrategy
RouteContext
```

典型解析链路：

```text
Business ShardKey
       ↓
CompositeShardKey
       ↓
StorageRouteResolver
       ↓
RouteMappingStrategy
       ↓
StorageRoute
       ↓
PhysicalStorageLocation
       ↓
Relational Access / DataSource
```

当前已经围绕：

```text
10 个物理库
×
每库 10 张分表
```

进行了 Direct DataSource 端到端验证。

业务代码不应该直接感知：

```text
db_03
table_07
DataSource
Connection
ShardingSphere Hint
```

业务只提供稳定的分片语义：

```java
ShardKey shardKey = ShardKey.of("userId", userId);
```

由 Storage Routing 负责将它解析为实际存储位置。

### 与 Idempotent 的协同

Storage Routing 当前已经进入幂等 JDBC 主链路。

一次幂等执行中：

```text
CompositeShardKey
    ↓
StorageRouteResolver
    ↓
StorageRoute
    ├── Business Repository
    └── Idempotency Repository
```

同一次执行只计算一次 Route，避免业务表和幂等表分别路由。

在可以共址的场景下：

```text
Business SQL
+
Idempotency SQL
```

可以共享：

```text
同一个物理数据库
同一个本地事务
同一个 JDBC Connection
```

### 为什么不直接绑定 `% databaseCount`

第一阶段可以使用 Hash / Modulo 作为简单路由策略，但长期扩容不能把：

```java
hash(key) % databaseCount
```

直接作为不可变的物理数据位置协议。

否则数据库数量发生变化时，大量 Key 会重新映射，造成大规模数据迁移。

因此后续会继续引入：

```text
Logical Shard / Virtual Bucket
        ↓
Route Mapping
        ↓
Physical Storage
```

让业务路由保持稳定，而物理位置可以独立迁移。

### 扩容与迁移方向

规划中的典型迁移过程：

```text
1. Prepare
   创建新节点 / 新表 / 新 Mapping

2. Snapshot Migration
   搬迁存量数据

3. Incremental Sync
   CDC / Binlog 同步增量变化

4. Cutover
   数据校验完成后切换 Mapping

5. Cleanup
   观察稳定后清理旧副本
```

对于无法安全在线切换的场景，也允许通过短时间只读 / 局部写冻结窗口完成最终收口。

Storage Routing 本身不会重新实现 CDC、ShardingSphere 或分布式数据库，而是负责：

```text
统一 ShardKey
统一 Route
统一 Mapping
统一上下文
统一 Adapter 边界
```

后续计划继续提供：

```text
Direct DataSource Adapter
ShardingSphere-JDBC Adapter
```

使业务层保持同一套路由 API。

---

# 推荐阅读入口

| 目标 | 文档 |
|---|---|
| Maven 架构 | `component/MAVEN-ARCHITECTURE.md` |
| 并发组件 | `component/concurrency-component/README.md` |
| 并发组件详细设计 | `component/concurrency-component/docs/README.md` |
| Foundation | `component/foundation-component/README.md` |
| 幂等组件 | `component/idempotent-component/README.md` |
| 幂等架构 | `component/idempotent-component/docs/architecture.md` |
| 分布式锁 | `component/distributed-lock-component/readme.md` |
| 分布式锁图集 | `component/distributed-lock-component/docs/README.md` |
| 消息组件 | `component/message-component/README.md` |
| 消息组件文档目录 | `component/message-component/docs/README.md` |
| 消息当前进度 | `component/message-component/docs/12-current-progress-and-next-steps.md` |
| 缓存组件 | `component/cache-component/readme.md` |
| 治理组件 | `component/governance-component/readme.md` |
| Storage Routing | `component/storage-routing-component/README.md` |

---

# Roadmap

## 近期

```text
Storage Routing v1 收口
├── Direct DataSource 10×10 E2E 完善
├── Route Mapping 抽象继续稳定
├── Idempotent Route-Aware Storage 收口
└── ShardingSphere-JDBC Adapter 设计

Reliable Consume
├── Message + Idempotent + Transaction
├── ACK / Redelivery / DLQ 统一语义
└── Kafka / Pulsar / RocketMQ4 Provider 一致性

Distributed Lock
├── Redis / Redisson 行为一致性
├── Watchdog / Fencing 完整测试
└── Metrics / 文档收口

Observability
├── Retry Metrics
├── Message Metrics
├── Lock Metrics
├── Cache Metrics
└── Prometheus / Grafana Dashboard
```

## 下一阶段

```text
Reliable Send
    Message + Retry + Outbox

Persistent Retry / Task
    长周期恢复、扫描、Claim / Lease

Storage Migration
    Virtual Bucket / Logical Shard
    + CDC
    + Route Mapping Version
    + Snapshot / Incremental / Cutover

Consistency
    Outbox
    + 补偿
    + Dead Letter
    + 人工重放

Data Access Governance
    Storage Routing
    + Relational Access
    + ShardingSphere-JDBC
    + SQL 治理
```

---

# 设计取舍

## 不做大而全的超级组件

组件体系的核心不是把所有能力塞进一个 jar，而是保留清晰边界：

```text
Retry           负责失败后是否短时间再试
Idempotent      负责重复执行不产生重复结果
Transaction     负责本地一致性边界
DistributedLock 负责多节点互斥和旧 Owner 防护
Message         负责 MQ 生命周期和 Provider 适配
StorageRouting  负责逻辑分片到物理存储的解析
RelationalAccess负责统一关系型访问语义
Task / Outbox   负责持久化恢复和最终一致性
Governance      负责稳定性策略
Observability   负责看见运行状态
```

---

## 不重新实现成熟基础设施

Redis、Kafka、Pulsar、RocketMQ、OpenTelemetry、Resilience4j、MyBatis、Spring Transaction、ShardingSphere 都是成熟基础设施。

本项目更关注：

```text
业务语义
    ↓
稳定 API
    ↓
Core 编排
    ↓
SPI / Integration
    ↓
Provider / Adapter
    ↓
成熟基础设施
```

---

## 优先让业务代码面向 API

业务项目推荐依赖：

```text
xxx-api
xxx-starter
```

不推荐业务代码直接绑定：

```text
xxx-core
xxx-provider-redis
xxx-provider-kafka
xxx-provider-pulsar
ShardingSphere API
Redis SDK
MQ SDK
```

Provider / Adapter 应由运行环境和自动装配选择，业务代码尽量不感知底层实现。

---

# 最终目标

最终希望形成一套能在真实 Java / Spring Boot 业务系统中组合使用的技术底座：

```text
Foundation
  + Concurrency
  + Retry
  + Transaction
  + Idempotent
  + Distributed Lock
  + Message
  + Cache
  + Governance
  + Observability
  + Relational Access
  + Storage Routing
  + Task / Outbox / Consistency
```

让业务开发更多关注：

```text
订单
支付
营销
清结算
客户
账户
风控
```

等业务本身，而不是每个系统都重复实现：

```text
线程池
重试
幂等
分布式锁
MQ 适配
ACK
超时
分库分表路由
迁移路由
补偿
Trace
Metrics
```
