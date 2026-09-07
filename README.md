# Java 技术组件体系

> 一套面向 Java / Spring Boot 分布式业务系统的可复用技术组件底座。

本项目用于沉淀业务系统中反复出现的基础技术问题，包括：

* 异步执行与线程池治理
* 有限重试
* 本地事务执行
* 请求与消息幂等
* 分布式锁
* 消息发送与消费
* 缓存
* 可观测性
* 服务治理
* 最终一致性与任务恢复

项目目标不是重新实现 Redis、Kafka、SkyWalking、ShardingSphere 等基础设施，而是在这些成熟基础设施之上提供：

> **统一抽象、统一接入、统一治理、统一观测和可组合的技术能力。**

---

# 1. 为什么有这个项目

在实际业务系统中，经常会重复遇到这些问题：

```text
接口被重复提交怎么办？

MQ 消息重复消费怎么办？

调用下游偶发超时，应该怎么安全重试？

多个实例同时执行同一个任务怎么办？

异步任务超时、失败、取消如何统一处理？

业务执行和幂等状态如何保证一致？

Kafka、Pulsar、RocketMQ 是否可以使用统一 API？

长时间失败任务如何恢复？

怎么统一记录 Metrics、Trace 和组件运行状态？
```

这些问题如果由每个业务项目分别解决，最终通常会出现：

* 重复开发
* 实现方式不统一
* 边界不清晰
* 异常处理不一致
* 缺少监控
* 技术方案难以升级
* 不同项目之间无法复用

因此，本项目尝试将这些能力逐步沉淀为一套独立的技术组件体系。

---

# 2. 当前组件

| 组件               | 当前状态       | 主要用途                              |
| ---------------- | ---------- | --------------------------------- |
| Foundation       | ✅ 可使用      | ID、时间、错误、序列化、校验等基础能力              |
| Concurrency      | ✅ 可使用      | 异步执行、线程池、超时、取消、组合任务               |
| Retry            | 🟢 核心能力已完成 | 有限重试、异常分类、退避策略                    |
| Transaction      | 🟢 主体完成    | 统一事务执行模板                          |
| Idempotency      | 🟢 主体完成    | RPC、消息、任务等场景的统一幂等                 |
| Distributed Lock | 🟡 收口中     | Redis 分布式锁、续租、Fencing Token       |
| Message          | 🟡 建设中     | Kafka / Pulsar / RocketMQ 统一发送与消费 |
| Cache            | 🟡 已有基础    | Caffeine + Redis 多级缓存             |
| Observability    | 🔵 待统一建设   | Metrics、Trace、日志、事件               |
| Governance       | 🔵 设计阶段    | 限流、熔断、隔离、超时、降级                    |
| Task             | 🔵 设计阶段    | 长周期任务、恢复、Claim / Lease            |
| Consistency      | ⚪ 规划阶段     | Outbox、补偿、死信、人工重放                 |
| Sharding         | ⚪ 方案研究     | 数据路由与分库分表                         |

状态说明：

```text
✅ 可使用      已形成相对稳定能力
🟢 主体完成    核心能力已经具备，仍可能继续增强
🟡 收口/建设中 已有代码或完整设计，正在完善
🔵 设计阶段    已明确方向，尚未形成正式稳定实现
⚪ 规划阶段    暂未进入当前建设主线
```

---

# 3. 根据问题选择组件

## 3.1 接口可能被重复提交

推荐：

```text
Idempotency
```

适用于：

* 创建订单
* 提交申请
* RPC 请求
* 表单重复提交
* 同一个业务动作重复触发

目标：

> 相同业务请求重复到达时，只让业务真正生效一次。

---

## 3.2 MQ 消息可能重复消费

推荐：

```text
Message
+
Idempotency
+
Transaction
```

用于解决：

```text
Broker 重投
网络异常
Consumer 重启
ACK 丢失
重复消息
```

目标：

> 消息允许重复到达，但业务结果不能重复产生。

---

## 3.3 远程调用偶发失败

推荐：

```text
Retry
```

适用于：

* 网络瞬时抖动
* HTTP 502 / 503 / 504
* Redis 短暂异常
* 数据库死锁
* Broker 瞬时不可用

支持的核心思想包括：

```text
最大尝试次数
总执行时间
异常分类
固定退避
指数退避
随机抖动
不可重试异常
```

重试组件不会默认对所有异常进行重试。

---

## 3.4 多个实例不能同时操作同一资源

推荐：

```text
Distributed Lock
```

典型场景：

* 定时任务抢占
* 缓存重建
* 同一订单处理
* 同一批次执行
* 分布式资源互斥

组件目标：

> 在多个 JVM / Pod 之间建立统一互斥控制。

---

## 3.5 需要防止旧锁持有者继续修改数据

推荐：

```text
Distributed Lock
+
Fencing Token
```

普通分布式锁解决：

> 当前谁拥有锁。

Fencing Token 进一步解决：

> 已经过期的旧 Owner 即使继续执行，也不能覆盖新 Owner 的结果。

适合对并发正确性要求较高的资源更新场景。

---

## 3.6 CompletableFuture 和线程池越来越难管理

推荐：

```text
Concurrency
```

统一解决：

* 线程池管理
* 任务提交
* Queue Timeout
* Execution Timeout
* Cancel
* Interrupt
* Fallback
* 多任务组合
* `allOf`
* `anySuccess`

避免业务系统自行维护大量线程池和 CompletableFuture 辅助代码。

---

## 3.7 多个数据库动作需要统一事务

推荐：

```text
Transaction
```

用于提供统一事务执行边界。

例如：

```text
创建业务数据
+
写幂等状态
+
更新其他关联数据
```

应在同一本地事务中完成时，可以通过事务模板统一控制。

---

## 3.8 业务需要 Redis + 本地缓存

推荐：

```text
Cache
```

当前主要方向：

```text
Caffeine
+
Redis
```

用于：

* 热点数据
* 读多写少数据
* 低延迟查询
* 数据库减压

后续继续完善：

* 本地缓存失效
* 防击穿
* 防穿透
* 防雪崩
* 缓存指标

---

# 4. 组件不是孤立使用的

本项目更强调：

> **组件组合，而不是构建一个无所不能的大组件。**

---

## 4.1 可靠消息消费

推荐组合：

```text
Message
+
Idempotency
+
Transaction
+
Retry
```

分别解决：

```text
Message
    → 消息接收、ACK、Broker 重投

Idempotency
    → 重复消息安全

Transaction
    → 幂等状态与业务修改一致

Retry
    → 判断失败是否值得再次执行
```

典型流程：

```text
Broker
   ↓
Message Consumer
   ↓
Idempotency Check
   ↓
Transaction
   ↓
Business Handler
   ↓
Commit
   ↓
ACK
```

执行失败：

```text
Business Failed
      ↓
Rollback
      ↓
Retry Decision
      ↓
RETRY / DLQ / DROP
```

---

# 5. 可靠消息发送

消息发送不仅是：

```text
producer.send(message)
```

还需要考虑：

* Broker 暂时不可用
* 网络超时
* Provider 内部重试
* 逻辑发送重试
* 最终发送失败
* Outbox
* 事务一致性

因此推荐：

```text
Message
+
Retry
+
Transaction / Outbox
```

其中需要特别避免：

```text
业务重试
×
Message Component 重试
×
MQ Client 重试
```

造成请求指数级放大。

---

# 6. 分布式任务

未来推荐组合：

```text
Task
+
Retry
+
Idempotency
+
Distributed Lock / Claim
```

分别负责：

```text
Task
    → 调度和任务生命周期

Retry
    → 失败后是否再次执行

Idempotency
    → 重复执行安全

Claim / Lock
    → 多节点执行权
```

适用于：

* 超时扫描
* 批处理
* 对账
* 补偿任务
* 长周期 Retry
* 服务重启恢复

---

# 7. 基础组件之间的职责区别

这些概念容易混淆。

## Retry 与 Idempotency

```text
Retry
=
失败以后是否再次执行

Idempotency
=
再次执行时如何避免重复结果
```

例如：

```text
调用支付接口超时
```

Retry 可以决定：

> 是否再调用一次。

Idempotency 则保证：

> 再调用一次不会重复扣款。

---

## Idempotency 与 Distributed Lock

```text
Distributed Lock
=
同一时刻只允许一个执行者进入

Idempotency
=
无论来了多少次，相同业务动作最终只生效一次
```

锁并不能代替幂等。

幂等也不能完全代替互斥。

---

## Retry 与 Compensation

```text
Retry
=
重新执行原操作

Compensation
=
执行另外一个操作修正状态
```

例如：

```text
支付调用失败
→ Retry

支付成功但订单失败
→ Refund Compensation
```

两者不是同一个问题。

---

## Transaction 与 Distributed Transaction

当前 Transaction Component 主要解决：

```text
本地事务
```

而不是重新实现：

```text
XA
TCC
Seata
Saga Framework
```

跨系统最终一致性会通过：

```text
Message
Outbox
Idempotency
Task
Compensation
```

等能力组合解决。

---

# 8. Message Component

Message Component 的目标是让业务代码不直接绑定某一个 MQ SDK。

目前主要支持方向：

```text
Kafka
Pulsar
RocketMQ
```

业务统一面对消息抽象：

```text
Message
MessageProvider
SendResult
ConsumeContext
```

而不是直接面对：

```text
KafkaTemplate
ConsumerRecord
PulsarClient
RocketMQTemplate
```

这样业务代码可以尽量保持 MQ Provider 无关。

---

# 9. Distributed Lock

当前分布式锁主要以 Redis Provider 为第一阶段实现。

已经覆盖或正在收口：

* 获取锁
* 释放锁
* Renew
* Check Held
* Wait
* Owner Token
* Watchdog
* Fencing Token
* Lock Result
* Lock Status
* Lock Stage

未来 Provider 可以继续扩展：

```text
Redis
Zookeeper
Etcd
```

而使用者面对的 API 保持稳定。

---

# 10. Retry

Retry Component 负责有限、可控、可观测的重试。

当前设计原则：

```text
默认不重试
显式声明可重试异常
最大次数包含第一次执行
支持退避
支持最大总耗时
中断立即停止
有副作用操作必须自行保证幂等
```

短时间 Retry 由 Retry Component 完成。

长时间 Retry：

```text
30 秒
5 分钟
1 小时
跨服务重启
```

未来交给：

```text
Task
Message Delay
Persistent Retry
```

承载。

Retry Component 本身不会变成一个任务调度平台。

---

# 11. Idempotency

Idempotency Component 用于统一处理：

```text
RPC Idempotency
Message Idempotency
Task Idempotency
```

组件负责：

* 幂等状态
* 并发重复判断
* 处理中状态
* 成功状态
* 失败恢复
* Result Policy
* 执行状态机
* 事务集成

调用方不需要在每个业务系统重新实现：

```text
SELECT
INSERT
UPDATE
重复判断
状态判断
超时占用
```

等逻辑。

---

# 12. Transaction

Transaction Component 提供统一事务模板。

核心目标：

> 将事务执行语义从具体 Spring Transaction API 中进一步抽象出来，使其他技术组件能够安全组合事务能力。

主要服务：

```text
Idempotency
Message Reliable Consume
Outbox
Database Retry
```

当前首先以本地事务作为主要能力范围。

---

# 13. Concurrency

Concurrency Component 解决业务项目中常见的线程池和异步执行失控问题。

主要目标：

```text
统一创建
统一命名
统一管理
统一超时
统一失败
统一取消
统一 Metrics
```

而不是让每个业务模块自行：

```java
Executors.newFixedThreadPool(...)
```

---

# 14. Foundation

Foundation 是整个组件体系最底层能力。

主要包括：

```text
foundation-core
foundation-time
foundation-id
foundation-error
foundation-serialization
foundation-json
foundation-validation
foundation-test
```

Foundation 保持：

* 小
* 稳定
* 无业务语义
* 尽量不依赖大型框架

技术组件可以依赖 Foundation。

Foundation 不反向依赖上层技术组件。

---

# 15. 可观测性

可观测性未来统一解决：

```text
Metrics
Trace
Log
MDC
Event
Health
```

项目不会重新实现 SkyWalking。

推荐关系：

```text
Business / Components
        ↓
Observability Abstraction
        ↓
Micrometer / OpenTelemetry
        ↓
SkyWalking / Prometheus / Grafana
```

因此后续替换观测平台时，不需要让所有业务代码直接迁移。

---

# 16. 技术栈

当前长期技术基线：

```text
Java 17
Spring Boot 3
Maven Multi Module
```

主要基础设施方向：

```text
Redis
MySQL / PostgreSQL
Kafka
Pulsar
RocketMQ
Prometheus
Grafana
OpenTelemetry
SkyWalking
XXL-Job
```

不同基础设施不会全部由本项目重新实现。

---

# 17. 推荐使用原则

## 原则一：只引入真正需要的组件

不要因为项目提供所有组件，就一次性全部引入。

例如普通 CRUD 服务可能只需要：

```text
Foundation
+
Idempotency
+
Observability
```

---

## 原则二：优先使用 Starter

对于 Spring Boot 应用，后续组件会尽量提供：

```text
xxx-spring-boot-starter
```

通过自动装配完成默认接入。

---

## 原则三：业务代码面向 API

业务尽量依赖：

```text
xxx-api
```

而不是：

```text
xxx-core
xxx-provider-redis
xxx-provider-kafka
```

---

## 原则四：Provider 由基础设施决定

例如：

```text
Distributed Lock API
        ↓
Redis Provider
```

未来可以替换为：

```text
Etcd Provider
```

业务代码原则上不需要修改。

---

# 18. 当前建设路线

当前重点：

```text
Distributed Lock 收口
        ↓
Message Reliable Consume
        ↓
Message Reliability 闭环
        ↓
Task / Timeout Scanner
        ↓
Consistency & Compensation
        ↓
Governance
        ↓
Data Access Governance
```

目前 Message Reliable Consume 是主要建设方向。

---

# 19. Roadmap

## Phase 1：基础执行能力

```text
Foundation
Concurrency
Retry
Transaction
Idempotency
Distributed Lock
```

目标：

> 建立单服务和多节点环境下最基础的可靠执行能力。

---

## Phase 2：跨系统可靠性

```text
Message
Reliable Send
Reliable Consume
Task
Consistency
```

目标：

> 建立系统之间的异步通信、失败恢复和最终一致性能力。

---

## Phase 3：治理能力

```text
Observability
Rate Limit
Circuit Breaker
Bulkhead
Configuration Governance
```

目标：

> 建立统一稳定性与运行治理体系。

---

## Phase 4：数据治理

```text
Sharding
Read/Write Routing
SQL Governance
Data Access Routing
```

目标：

> 面向更大数据量和复杂系统提供统一数据访问治理。

---

# 20. 文档

项目采用两类文档。

## 使用文档

面向组件使用者：

```text
README.md
```

主要回答：

```text
有什么？
解决什么？
什么时候用？
怎么组合？
怎么接入？
```

---

## 设计与建设文档

面向组件开发者和维护者：

```text
docs/技术组件建设进展.md
```

主要记录：

* 架构设计
* 模块拆分
* API / Core / SPI / Provider
* 状态机
* 内部时序
* 关键技术决策
* 当前研发进度

---

# 21. 项目设计理念

本项目并不追求：

> “所有东西全部自己实现。”

而更关注：

> **如何把成熟基础设施封装成业务真正容易使用、可以组合、可以治理、可以演进的技术能力。**

因此很多组件最终都会采用：

```text
稳定 API
    ↓
Core
    ↓
SPI
    ↓
Provider
    ↓
成熟基础设施
```

例如：

```text
DistributedLockClient
        ↓
Lock Core
        ↓
LockProvider
        ↓
Redis
```

或者：

```text
Message API
      ↓
Message Core
      ↓
MessageProvider
      ↓
Kafka / Pulsar / RocketMQ
```

---

# 22. 最终目标

最终希望形成一套能够在真实业务项目中组合使用的基础能力：

```text
Foundation
    +
Concurrency
    +
Retry
    +
Transaction
    +
Idempotency
    +
Distributed Lock
    +
Message
    +
Task
    +
Observability
    +
Governance
```

让业务开发更多关注：

```text
订单
支付
营销
清结算
客户
账户
```

而不是每一个系统都重新实现：

```text
重试
幂等
分布式锁
线程池
MQ 适配
ACK
超时
补偿
Trace
Metrics
```

这就是整个技术组件体系长期建设的目标。
