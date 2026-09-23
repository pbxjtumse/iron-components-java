# Iron Components Java

一套面向 Java / Spring Boot 分布式业务系统的可复用技术组件体系。

这个仓库不是为了重新实现 Redis、Kafka、Pulsar、RocketMQ、OpenTelemetry、Resilience4j、ShardingSphere 这些基础设施，而是在成熟基础设施之上沉淀一层更贴近业务系统的统一能力：

- 统一 API：业务代码优先依赖 api / starter，而不是直接绑定某个中间件 SDK。
- 统一编排：把重试、幂等、事务、锁、消息、缓存、治理、观测这些能力组合起来。
- 统一治理：错误分类、超时、重试、限流、熔断、指标、日志、链路追踪尽量形成一致语义。
- 统一演进：Provider 可替换，业务代码尽量不跟 Redis / MQ / JDBC / OTel 的细节强耦合。

当前仓库同时包含一个 COLA 分层样例应用和一组可复用技术组件。真正的主线在 component 目录。

## 技术基线

| 项目 | 当前选择 |
|---|---|
| JDK | Java 17 |
| 构建 | Maven Multi Module |
| 应用框架 | Spring Boot 3.5.x |
| 应用分层 | COLA 风格 client / adapter / app / domain / infrastructure / start |
| 组件版本 | 1.0.0-SNAPSHOT |
| 主要基础设施方向 | Redis、MySQL / PostgreSQL、Kafka、Pulsar、RocketMQ4、OpenTelemetry、Micrometer、Resilience4j |
| 测试与验证 | JUnit、Testcontainers、ArchUnit、组件 Demo、POM 校验脚本 |

## 仓库结构

~~~text
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
~~~

## 当前组件全景

| 组件 | 当前代码模块 | 当前状态 | 主要解决的问题 |
|---|---|---|---|
| Foundation | foundation-core / time / id / codec / context / reflection / resource / serialization / test-support | 可作为底层依赖使用 | 通用工具门面、时间、ID、序列化、上下文、测试支撑 |
| Concurrency | api / config / core / provider / integrations / starter / demo | 一期能力较完整 | 线程池、异步任务、排队超时、执行超时、fallback、取消、任务状态、指标 |
| Retry | api / core / config / demo | 核心能力已落地 | 显式、有限、可观测的进程内重试 |
| Transaction | api / spi / core / provider-spring / starter / demo | 本地事务抽象已落地 | 为幂等、消息、可靠任务提供统一事务执行边界 |
| Idempotent | api / core / provider-redis / provider-jdbc / integration / starter / demo | 主链路已成型 | RPC、消息、任务等场景的幂等状态机、结果回放、恢复接管 |
| Distributed Lock | api / spi / core / redis / redisson / jdbc-fencing / starter / demo | 收口完善中 | 分布式互斥、续租、Watchdog、Owner Token、Fencing Token |
| Message | api / spi / core / Kafka / Pulsar / RocketMQ4 / starter / demo | 一期普通收发完成，发送可靠性验证中 | 多 MQ 统一消息模型、Provider 适配、可靠发送、后续可靠消费 |
| Cache | api / core / config / provider-caffeine / provider-redis / composite / integrations / starter / demo | 基础能力和二期设计并行 | Caffeine + Redis 多级缓存、缓存策略、击穿治理、失效事件 |
| Governance | api / model / core / spi / configs / runtime / engine / integration / starter / demo | 建设中 | 限流、熔断、隔离、超时、治理策略、Resilience4j 适配 |
| Observability | api / core / otel / starter / demo | 建设中 | Metrics、Trace、事件、OpenTelemetry 接入 |
| Relational Access | api / spi / core / integration-spring / starter | 基础抽象已落地 | 关系型数据库访问统一抽象，给路由、幂等、事务等组件复用 |
| Storage Routing | api / core / integration-relational / starter | v1 建设中 | 分库分表、读写分离、多租户、冷热数据等存储路由上下文 |

状态说明：

| 状态 | 含义 |
|---|---|
| 可作为底层依赖使用 | 代码结构相对稳定，适合被其他组件依赖 |
| 一期能力较完整 | 核心场景已经覆盖，后续主要增强治理、观测和边界场景 |
| 主链路已成型 | API、Core、Provider 和主要执行链路已具备，仍会继续补测试和联调 |
| 收口完善中 | 设计与代码都有主体，但仍在处理边界、文档、验证和一致性问题 |
| 建设中 | 模块已建立，正在补齐稳定实现、集成和验证 |
| v1 建设中 | 第一版抽象和集成正在推进，还不应视为稳定发布能力 |

## 组件分层原则

大部分组件遵循相近的分层方式：

~~~text
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
~~~

不是每个组件都完全拥有这些层，但整体方向一致。

## Maven 架构

component/pom.xml 是技术组件源码的 Aggregator、Parent 和内部 dependencyManagement。

component/component-bom/pom.xml 是对外发布的 BOM。业务项目通常不继承 component/pom.xml，而是 import component-bom 后按需引入 starter 或 api。

内部开发推荐从仓库根目录使用 Maven Reactor：

~~~bash
mvn -pl component/message-component -am clean compile
mvn -pl component/idempotent-component -am clean compile
mvn -pl component/distributed-lock-component -am clean compile
~~~

-am 会把目标组件依赖的其他 Reactor 模块一起构建。

外部业务工程消费方式：

~~~xml
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
~~~

之后按需引入：

~~~xml
<dependency>
    <groupId>com.xjtu.iron</groupId>
    <artifactId>idempotent-starter</artifactId>
</dependency>

<dependency>
    <groupId>com.xjtu.iron</groupId>
    <artifactId>message-starter</artifactId>
</dependency>
~~~

## 快速构建

全量校验脚本：

~~~bash
bash scripts/build-first.sh
~~~

脚本会依次执行：

1. 校验 POM 聚合、父链和 component-bom。
2. 校验源码直接使用的第三方 API 是否在模块 POM 中声明。
3. 编译全部生产代码。
4. 执行全部测试并打包。

如果只是开发某个组件，建议优先使用局部构建：

~~~bash
mvn -U -pl component/foundation-component -am clean verify
mvn -U -pl component/concurrency-component -am clean verify
mvn -U -pl component/message-component/message-demo-springboot -am clean package -DskipTests
~~~

## 典型组合关系

这些组件不是孤立存在的。真正的价值在于组合。

### 接口防重复提交

~~~text
Idempotent
    + Transaction
    + Redis / JDBC Provider
~~~

适合创建订单、提交申请、支付请求、营销领取、人工操作等场景。

幂等组件负责判断同一个逻辑请求是否已经处理过；事务组件负责把业务写入和幂等状态更新放在一致边界内。

### MQ 可靠消费

~~~text
Message
    + Idempotent
    + Transaction
    + Retry
    + Observability
~~~

Message 负责接收、ACK、Provider 映射和消费上下文。

Idempotent 负责重复消息安全。

Transaction 负责业务写入和幂等状态一致。

Retry 负责短时间、有限次数、可解释的失败重试。

Observability 负责把消费状态、异常、耗时、重试次数暴露出来。

### MQ 可靠发送

~~~text
Message
    + Retry
    + Transaction / Outbox
    + Observability
~~~

当前 message-component 一期普通收发已经覆盖 Kafka、Pulsar、RocketMQ4。二期发送可靠性已经接入 RetryExecutor 和 DefaultReliableMessageSender，仍需要继续完成本地完整编译、三 MQ 可靠发送联调和 Provider 异常映射校验。

### 分布式互斥与防旧写

~~~text
Distributed Lock
    + Fencing Token
    + Relational Access / JDBC
~~~

普通分布式锁解决“当前谁能进入”。

Fencing Token 进一步解决“旧 Owner 即使锁过期后继续执行，也不能覆盖新 Owner 的结果”。

### 异步并行与治理

~~~text
Concurrency
    + Governance
    + Observability
~~~

Concurrency 负责线程池、任务提交、超时、fallback、取消和任务状态。

Governance 负责限流、隔离、熔断等稳定性策略。

Observability 负责输出统一指标和链路标签。

### 多级缓存

~~~text
Cache
    + Caffeine Provider
    + Redis Provider
    + Distributed Lock
    + Governance
    + Observability
~~~

Cache 当前方向是 Caffeine + Redis 二级缓存。

后续重点是多实例本地缓存失效通知、分布式互斥加载、动态 CacheSpec、缓存事件模型、治理接入和慢日志指标。

### 存储路由

~~~text
Storage Routing
    + Relational Access
    + Idempotent
~~~

Storage Routing 用来统一承载分库分表、读写分离、多租户、冷热数据等路由上下文。

Relational Access 提供关系型访问抽象。

Idempotent 可以基于 routeKey / policyName 等信息与存储路由协同，避免幂等表、业务表、路由上下文割裂。

## 核心组件说明

### Foundation Component

定位：全仓库底层技术门面。

当前模块：

~~~text
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
~~~

当前版本强调薄封装：优先复用 JDK 17、Apache Commons、Jackson 等成熟能力，避免重新发明大量工具类。

命名上避免 StringUtils、CollectionUtils 这类容易与开源库冲突的类名，统一使用 IronStrings、IronCollections、IronLists、IronMaps、IronDigests 等门面。

### Concurrency Component

定位：统一异步执行和线程池治理。

主要能力：

- 多线程池配置和注册。
- 异步 run / supply / submit / execute。
- Queue Timeout 和 Execution Timeout。
- fallback、取消、interrupt。
- TaskHandle、TaskExecutionRegistry、TaskExecutionListener。
- 错误结构化和任务状态管理。
- Micrometer 指标和后续治理集成。

适合远程接口并行查询、批量分片处理、后台异步任务、清结算/营销/风控类并行执行场景。

### Retry Component

定位：显式、有限、可观测的短周期重试。

设计原则：

- 默认不对所有异常盲目重试。
- 最大次数包含第一次执行。
- 支持异常分类、固定退避、指数退避、随机抖动。
- 支持最大总耗时。
- 中断应立即停止。
- 有副作用操作必须由调用方保证幂等。

Retry 不负责长周期任务调度。分钟级、小时级、跨重启的恢复应交给 Message Delay、Task、Outbox 或持久化恢复任务。

### Transaction Component

定位：统一本地事务执行抽象。

当前模块：

~~~text
transaction-api
transaction-spi
transaction-core
transaction-provider-spring
transaction-spring-boot-starter
transaction-demo-mybatis
transaction-demo-jpa
~~~

它不是 XA、TCC、Saga 或 Seata 的替代品。当前重点是为组件内部组合提供统一本地事务边界，例如幂等状态、业务写入、消息可靠消费、Outbox 等。

### Idempotent Component

定位：统一幂等执行状态机。

当前支持两类生命周期：

| 类型 | 说明 | 推荐存储 |
|---|---|---|
| WINDOWED | 有限时间窗口内幂等 | Redis |
| DURABLE | 长期业务事实幂等 | JDBC |

关键模型：

| 模型 | 含义 |
|---|---|
| IdempotencyRequest | key、requestHash、routeKey、policyName |
| IdempotencyPolicy | mode、namespace、repository、timeout、window、recovery、lock |
| Repository | Redis Lua / JDBC UNIQUE / row lock / CAS |
| ownerToken + version | 当前执行 generation 身份 |
| StateMachine | EXECUTE / REPLAY / RETURN |
| ResultPolicy | NONE / SNAPSHOT / REFERENCE |
| RecoveryPolicy | 判断哪些异常状态允许可靠任务接管 |

幂等不等于分布式锁。锁可以降低并发竞争，但幂等正确性应来自 Repository 的原子状态转移和唯一约束。

### Distributed Lock Component

定位：分布式资源互斥和 Fencing Token。

当前模块：

~~~text
distributed-lock-api
distributed-lock-spi
distributed-lock-core
distributed-lock-provider-redis
distributed-lock-provider-redisson
distributed-lock-fencing-provider-jdbc
distributed-lock-starter
distributed-lock-demo
~~~

主要能力：

- tryLock / execute。
- LockHandle unlock / renew / checkHeld / assertHeld。
- ownerToken。
- renew 和 Watchdog。
- wait strategy。
- LockStatus、LockStage。
- Redis Provider。
- Redisson Provider。
- JDBC Fencing Token Provider。
- 完整 L0-L3 PlantUML 文档。

### Message Component

定位：统一消息模型、生命周期治理和多 MQ Provider 适配。

当前模块：

~~~text
message-api
message-spi
message-core
message-integrations
message-starter
message-demo-springboot
~~~

Provider 方向：

| Provider | 当前状态 |
|---|---|
| Kafka | 普通收发已通过 |
| Pulsar | 普通收发已通过 |
| RocketMQ4 | 普通收发已通过 |
| 三 Provider 同时收发 | 已通过 |
| 发送可靠性 | 工程验证阶段 |
| 消费可靠性 | 设计和文档已展开，待进入实现收口 |

当前发送主链路：

~~~text
业务代码
  -> MessageTemplate
  -> MessageEnvelopeEnricher
  -> DestinationResolver
  -> MessageWireCodec
  -> MessageSendExecutor
  -> DirectMessageSender / DefaultReliableMessageSender
  -> MessageProvider
  -> Kafka / Pulsar / RocketMQ4
~~~

二期发送可靠性已经接入 retry-component。后续重点是三 MQ 可靠发送联调、异常映射校验、消费可靠性与幂等 / 事务集成。

### Cache Component

定位：统一缓存访问和多级缓存治理。

当前模块：

~~~text
cache-api
cache-core
cache-config
cache-provider
cache-integrations
cache-starter
cache-demo
~~~

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

### Governance Component

定位：稳定性治理抽象。

当前模块：

~~~text
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
~~~

设计边界：

- governance-core 不直接绑定 Resilience4j、Sentinel、Nacos。
- governance-api 不依赖 Spring。
- 业务代码不直接依赖具体治理框架。
- 具体限流、熔断、隔离能力通过 engine / provider 适配。

### Observability Component

定位：统一可观测性抽象和 OpenTelemetry 接入。

当前模块：

~~~text
observability-api
observability-core
observability-otel
observability-starter
observability-demo-app
~~~

目标是让组件统一输出 Metrics、Trace、事件和运行状态，而不是让每个组件直接散落 Micrometer / OTel / 日志细节。

### Relational Access Component

定位：关系型数据库访问基础抽象。

当前模块：

~~~text
relational-api
relational-spi
relational-core
relational-integration
relational-starter
~~~

它为 storage-routing、idempotent、transaction 等组件提供更统一的关系型访问语义，避免每个组件重复处理 DataSource、路由、SQL 执行上下文等问题。

### Storage Routing Component

定位：统一存储路由上下文。

当前模块：

~~~text
storage-routing-api
storage-routing-core
storage-routing-integration
storage-routing-starter
~~~

目标场景：

- 分库分表。
- 读写分离。
- 多租户。
- 冷热数据。
- 业务 routeKey 到物理存储位置的解析。
- 与 relational-access、idempotent 的协同。

## 推荐阅读入口

| 目标 | 文档 |
|---|---|
| Maven 架构 | component/MAVEN-ARCHITECTURE.md |
| 并发组件 | component/concurrency-component/README.md |
| 并发组件详细设计 | component/concurrency-component/docs/README.md |
| Foundation | component/foundation-component/README.md |
| 幂等组件 | component/idempotent-component/README.md |
| 幂等架构 | component/idempotent-component/docs/architecture.md |
| 分布式锁 | component/distributed-lock-component/readme.md |
| 分布式锁图集 | component/distributed-lock-component/docs/README.md |
| 消息组件 | component/message-component/README.md |
| 消息组件文档目录 | component/message-component/docs/README.md |
| 消息当前进度 | component/message-component/docs/12-current-progress-and-next-steps.md |
| 缓存组件 | component/cache-component/readme.md |
| 治理组件 | component/governance-component/readme.md |

## 当前建设重点

短期重点：

1. message-component 二期发送可靠性收口。
2. message-component 消费可靠性接入幂等、事务、ACK / redelivery 映射。
3. idempotent-provider-jdbc 与 storage-routing 协同。
4. distributed-lock 继续收口文档、边界和 Provider 一致性。
5. governance / observability 成为其他组件的统一接入点。
6. cache 二期围绕多实例失效、互斥加载、动态策略和指标展开。

中期重点：

1. Reliable Consume：Message + Idempotent + Transaction。
2. Reliable Send：Message + Retry + Outbox。
3. Persistent Retry / Task：长周期恢复、扫描、Claim / Lease。
4. Consistency：Outbox、补偿、死信、人工重放。
5. Data Access Governance：Storage Routing + Relational Access + SQL 治理。

## 设计取舍

### 不做大而全的超级组件

组件体系的核心不是把所有能力塞进一个 jar，而是保留清晰边界：

~~~text
Retry 负责失败后是否短时间再试
Idempotent 负责重复执行不产生重复结果
Transaction 负责本地一致性边界
Distributed Lock 负责多节点互斥
Message 负责 MQ 生命周期和 Provider 适配
Task / Outbox 负责持久化恢复和最终一致性
Governance 负责稳定性策略
Observability 负责看见运行状态
~~~

### 不重新实现成熟基础设施

Redis、Kafka、Pulsar、RocketMQ、OpenTelemetry、Resilience4j、MyBatis、Spring Transaction 都是成熟基础设施。

本项目更关注：

~~~text
业务语义
    -> 稳定 API
    -> Core 编排
    -> SPI
    -> Provider
    -> 成熟基础设施
~~~

### 优先让业务代码面向 API

业务项目推荐依赖：

~~~text
xxx-api
xxx-starter
~~~

不推荐直接依赖：

~~~text
xxx-core
xxx-provider-redis
xxx-provider-kafka
xxx-provider-pulsar
~~~

Provider 由运行环境和自动装配选择，业务代码尽量不感知底层实现。

## 最终目标

最终希望形成一套能在真实 Java / Spring Boot 业务系统中组合使用的技术底座：

~~~text
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
~~~

让业务开发更多关注订单、支付、营销、清结算、客户、账户、风控等业务本身，而不是每个系统都重复实现线程池、重试、幂等、分布式锁、MQ 适配、ACK、超时、补偿、Trace 和 Metrics。
