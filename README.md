# iron-components-java

> 一套面向 Java / Spring Boot 分布式业务系统的可复用技术组件底座。

`iron-components-java` 用于沉淀业务系统中反复出现、但又不应该由每个业务项目重复实现的基础技术能力。

当前主要覆盖：

* Foundation 基础能力
* 异步执行与线程池治理
* 有限重试
* 本地事务模板
* 通用幂等
* 分布式锁
* 消息发送与可靠消费
* Relational Access 关系型数据访问
* 缓存
* 可观测性
* 服务治理
* 最终一致性与任务恢复

项目目标并不是重新实现 Redis、Kafka、Pulsar、RocketMQ、MySQL、SkyWalking、ShardingSphere 等成熟基础设施，而是在这些基础设施之上建立：

> **统一抽象、统一接入、统一治理、统一观测、明确边界并且能够组合使用的 Java 技术能力。**

---

# 1. 为什么有这个项目

真实业务系统里，经常会重复遇到这些问题：

```text
接口被重复提交怎么办？

MQ 消息重复消费怎么办？

调用下游偶发超时，应该怎么安全重试？

多个 Pod 同时处理同一份数据怎么办？

线程池越来越多，超时、取消和异常如何统一？

幂等状态与业务数据库修改如何保持一致？

Kafka、Pulsar、RocketMQ 能否使用统一消息模型？

JDBC 操作是否可以拥有统一执行入口，而不是每个组件自己拼 JDBC？

大数据量幂等表、Outbox 表未来如何为分表做准备？

长时间失败任务如何恢复？

怎样统一记录 Metrics、Trace、Event 和组件运行状态？
```

如果所有业务项目各自解决这些问题，最终往往出现：

* 重复开发
* API 风格不一致
* 相同问题存在多套实现
* 事务边界模糊
* 重试与幂等互相混用
* 分布式锁被错误当作幂等
* MQ Provider 代码侵入业务
* JDBC 逻辑散落在组件内部
* 缺少统一指标与故障定位能力
* 技术方案难以升级和替换

因此，本项目希望逐步建立一套真正可以长期复用的技术组件体系。

---

# 2. 当前组件状态

> 更新时间：2026-09

| 组件                    | 当前状态               | 当前主要能力                                                    |
| --------------------- | ------------------ | --------------------------------------------------------- |
| **Foundation**        | ✅ 可使用              | ID、时间、错误、序列化、JSON、校验、测试基础                                 |
| **Concurrency**       | ✅ 可使用              | 异步执行、线程池、超时、取消、Fallback、组合任务                              |
| **Retry**             | ✅ 核心可使用            | 有限重试、异常分类、结果分类、退避策略                                       |
| **Transaction**       | ✅ 主体可使用            | 本地事务模板、事务参与模型、组件事务集成                                      |
| **Idempotency**       | 🟢 v1 可使用 / v2 建设中 | 状态机、Owner、结果策略、事务集成、Shard-Ready Storage                   |
| **Distributed Lock**  | ✅ Redis 主体可使用      | Redis Lock、Lease、Renew、Watchdog、Owner Token、Fencing Token |
| **Message**           | 🟢 v1 可使用 / v2 建设中 | Kafka/Pulsar 统一发送消费、消费幂等、事务集成、可靠性演进                       |
| **Relational Access** | 🟡 V1 建设中          | 统一关系型数据库执行入口、SQL/参数/Row Mapping、事务边界协作                    |
| **Cache**             | 🟡 已有实现基础          | Caffeine + Redis 多级缓存                                     |
| **Observability**     | 🔵 骨架已有            | Metrics、Trace、MDC、Event、Health 统一方向                       |
| **Governance**        | 🔵 已有能力雏形          | Timeout、Bulkhead、Rate Limit 等治理能力                         |
| **Task**              | 🔵 架构设计            | 扫描、Claim、Lease、长周期执行与恢复                                   |
| **Consistency**       | ⚪ 下一阶段             | Outbox、本地消息表、补偿、死信、人工重放                                   |
| **Sharding**          | ⚪ 方案研究             | Shard Key、数据路由、分库分表与扩容策略                                  |

状态说明：

```text
✅ 可使用
核心能力和主要边界已经稳定，可以作为当前项目能力使用。

🟢 v1 可使用 / v2 建设中
第一阶段能力已经形成，正在继续建设可靠性或扩展能力。

🟡 建设中
已有正式设计和代码，仍在持续收口。

🔵 架构设计 / 骨架已有
方向已经明确，也可能已有部分代码，但尚未作为稳定组件完成。

⚪ 方案研究 / 下一阶段
已经进入整体技术蓝图，但当前不是主要实现线。
```

---

# 3. 根据问题选择组件

## 3.1 接口可能被重复提交

推荐：

```text
Idempotency
```

典型场景：

* 创建订单
* 提交申请
* RPC 请求
* 表单重复提交
* 同一业务指令重复到达
* 补偿任务重复执行

目标：

> 相同业务动作可以重复到达，但真正的业务副作用只能按照定义发生一次。

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

当前 Message Component 已经逐步将消费幂等与事务能力连接起来。

用于处理：

```text
Broker 重投
Consumer 重启
ACK 丢失
消费超时
网络异常
重复消息
业务执行失败
```

核心目标：

> **消息允许至少一次投递，但业务结果必须具备重复执行安全性。**

---

## 3.3 远程调用偶发失败

推荐：

```text
Retry
```

适用于：

* 网络瞬时抖动
* HTTP Timeout
* HTTP 502 / 503 / 504
* Redis 短暂异常
* JDBC 死锁
* Broker 瞬时故障

Retry Component 负责：

```text
最大尝试次数
最大执行时间
异常分类
结果分类
固定退避
指数退避
随机抖动
中断处理
重试结果
```

核心原则：

> **默认不重试，只有明确判断为可以重试的失败才执行 Retry。**

Retry 不保证业务幂等。

---

## 3.4 多个实例不能同时操作同一资源

推荐：

```text
Distributed Lock
```

典型场景：

* 分布式任务抢占
* 缓存重建
* 批次处理
* 同一资源并发修改
* 单实例逻辑在集群中的互斥执行

当前 Redis Provider 已经覆盖主要分布式锁能力。

---

## 3.5 需要防止旧锁持有者继续修改数据

推荐：

```text
Distributed Lock
+
Fencing Token
```

需要区分两个概念。

### Owner Token

回答：

> 当前请求是不是这把锁真正的 Owner？

### Fencing Token

回答：

> 当前请求是不是比之前的 Owner 更新？

因此：

```text
Owner Token
    → 解决锁归属

Fencing Token
    → 解决业务资源写入时序
```

即使旧 Owner 因为 GC、网络抖动等原因继续运行，资源侧仍然可以根据更大的 Fencing Token 拒绝旧请求。

---

## 3.6 CompletableFuture 和线程池越来越难管理

推荐：

```text
Concurrency
```

统一处理：

* Executor 管理
* Task Submission
* Queue Timeout
* Execution Timeout
* Cancel
* Interrupt
* Fallback
* `allOf`
* `allOfWithTimeout`
* `anySuccess`
* 线程池状态指标

目标不是包装 CompletableFuture，而是：

> **统一异步执行生命周期。**

---

## 3.7 多个数据库操作需要统一事务

推荐：

```text
Transaction
```

例如：

```text
创建业务记录
+
写入幂等状态
+
修改关联数据
```

这些动作需要保持本地事务一致性时，可以通过 Transaction Component 提供统一事务执行边界。

当前 Transaction Component 的重点是：

```text
Local Transaction
```

而不是重新实现 XA / TCC / Seata。

---

## 3.8 技术组件需要直接访问关系型数据库

推荐：

```text
Relational Access
```

这是当前新增并正在建设的重要基础组件。

它主要解决过去多个技术组件各自直接使用 JDBC 时产生的问题：

```text
每个组件自己管理 Connection
每个组件自己拼 PreparedStatement
每个组件自己处理参数
每个组件自己处理 ResultSet
每个组件自己决定事务
```

Relational Access 希望提供一个统一的底层关系型数据库访问入口。

第一阶段主要围绕：

```text
RelationalTemplate
SqlStatement
SQL Parameters
RowMapper
update
query
execute
```

展开。

一个非常重要的原则是：

> **Relational Access 不自行创建业务事务。**

事务边界仍然由：

```text
Transaction Component
```

或者宿主业务系统负责。

因此关系更接近：

```text
Idempotency
Outbox
Task Storage
其他技术组件
       ↓
Relational Access
       ↓
DataSource / JDBC
       ↓
MySQL / PostgreSQL
```

---

## 3.9 业务需要 Redis + 本地缓存

推荐：

```text
Cache
```

当前已有：

```text
Caffeine
+
Redis
```

多级缓存设计基础。

主要面向：

* 热点数据
* 读多写少数据
* 数据库减压
* 低延迟查询

后续继续完善：

* 缓存一致性
* 本地缓存失效
* 防穿透
* 防击穿
* 防雪崩
* Cache Metrics

---

# 4. 组件不是孤立使用的

`iron-components-java` 更强调：

> **通过多个边界清晰的小组件组合解决复杂问题，而不是创建一个什么都负责的大组件。**

例如可靠消息消费不会重新实现：

```text
Retry
Idempotency
Transaction
```

而是组合这些已有组件。

---

# 5. 可靠消息消费

当前推荐组合：

```text
Message
+
Idempotency
+
Transaction
+
Retry
```

各组件职责分别是：

```text
Message
    → 接收消息、消费执行、ACK / Retry / DLQ 映射

Idempotency
    → 解决重复消费

Transaction
    → 解决业务数据与幂等状态本地事务一致性

Retry
    → 判断什么失败值得再次执行
```

逻辑流程：

```text
Broker
   ↓
Message Consumer
   ↓
Consume Context
   ↓
Transaction Boundary
   ↓
Idempotency
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
Transaction Rollback
       ↓
Failure Classification
       ↓
RETRY / DLQ / DISCARD
```

当前可靠消费已经进入：

> **幂等能力 + Transaction Integration 的组合阶段。**

---

# 6. 为什么 ACK 必须在事务之后

错误方式：

```text
Receive
   ↓
ACK
   ↓
Business
```

如果 ACK 成功以后业务失败，Broker 会认为消息已经消费完成。

因此可靠消费更合理的顺序是：

```text
Receive
   ↓
Business Transaction
   ↓
Commit
   ↓
ACK
```

如果事务失败：

```text
Rollback
   ↓
不确认最终成功
   ↓
重新投递 / Retry Decision
```

这也是 Message Component 继续演进的重要方向。

---

# 7. 消息可靠发送

可靠发送不仅仅是：

```java
producer.send(message);
```

还需要考虑：

* Broker 暂时不可用
* 网络超时
* Provider Client 内部重试
* Message Component 外层重试
* 不确定发送结果
* 最终发送失败
* Outbox
* 本地事务一致性

当前推荐的能力组合：

```text
Message
+
Retry
+
Transaction
+
未来 Outbox
```

尤其需要避免：

```text
业务层 Retry
×
Message Component Retry
×
Kafka / Pulsar / RocketMQ Client Retry
```

造成重试放大。

---

# 8. Idempotency

Idempotency Component 当前已经不只是简单的：

```text
SETNX
```

或者：

```text
INSERT UNIQUE KEY
```

而是逐渐形成完整执行模型。

当前主要能力包括：

```text
IdempotencyKey
Owner Token
状态机
处理中状态
Success / Failed / Discarded
Result Policy
重复请求判断
执行结果处理
Transaction Integration
```

适用于：

```text
HTTP
RPC
Message
Task
```

等不同入口。

---

# 9. Idempotency v2：Shard-Ready Storage

当前幂等组件正在继续进入：

```text
idempotent-component v2
Shard-Ready Storage
```

阶段。

这一阶段的目标不是马上实现完整分库分表，而是先把存储模型设计成未来可扩展。

重点包括：

```text
幂等表结构
幂等状态机
ownerToken 抢占语义
PROCESSING 超时恢复
markSuccess
markFailed
markDiscarded
shard_key
scan_bucket
JDBC Storage
未来分表扩展点
```

核心思想是：

> **先让存储模型具备分片能力，再决定什么时候真正引入分库分表。**

而不是现在直接把整个系统绑定到某一个 Sharding 中间件。

---

# 10. Retry

Retry Component 当前定位为：

> **统一描述、判断和执行“某个操作是否应该再次执行”的可靠性执行组件。**

它负责：

```text
是否重试
最大次数
最大耗时
错误分类
结果分类
Backoff
Jitter
Retry Context
Retry Result
```

它不负责：

```text
业务幂等
事务原子性
消息 ACK
业务补偿
长周期任务调度
```

短时间 Retry：

```text
100ms
300ms
1s
```

可以留在 JVM 内部。

而：

```text
30 秒
5 分钟
1 小时
服务重启后继续
```

这种执行不应该依赖 Thread.sleep。

未来应该交给：

```text
Message Delay
Task Component
Durable Retry
```

等外部载体。

---

# 11. Retry 与 Idempotency

两者经常同时出现，但解决的是完全不同的问题。

```text
Retry
=
失败后是否再次执行

Idempotency
=
再次执行时是否产生重复业务结果
```

对于有副作用操作，推荐：

```text
Retry
   ↓
Idempotency
   ↓
Business
```

即每一次 Retry 都重新进入幂等保护。

---

# 12. Retry 与 Transaction

数据库事务发生：

```text
Deadlock
Lock Timeout
Temporary Connection Failure
```

等可以安全重试的问题时，一般应该重新执行：

> **完整事务单元。**

推荐：

```text
Retry
   ↓
Transaction
   ↓
完整业务事务
```

而不是：

```text
Transaction
   ↓
执行一半
   ↓
某一条 SQL Retry
```

从而避免长期持锁和局部执行语义混乱。

---

# 13. Distributed Lock

Distributed Lock Component 第一阶段主要采用 Redis Provider。

当前能力已经覆盖：

```text
tryLock
unlock
renew
checkHeld
assertHeld
wait
Watchdog
Owner Token
Fencing Token
```

使用者主要面对：

```text
DistributedLockClient
LockHandle
LockOptions
LockResult
```

而不直接操作：

```text
Redis Lua
Redis Client
Fencing Sequence
```

等 Provider 内部实现。

未来可以继续增加：

```text
Zookeeper
Etcd
```

Provider。

---

# 14. Transaction

Transaction Component 用于统一技术组件之间的本地事务能力。

其意义并不是替业务重新实现 Spring Transaction，而是：

> **为 Idempotency、Message、Outbox 等技术组件提供一个稳定的事务抽象边界。**

当前已经用于重点解决：

```text
Idempotency Transaction Integration
Message Reliable Consume
未来 Outbox
```

事务模板仍然坚持：

> 本地事务优先。

跨系统一致性不由 Transaction Component 单独承担。

---

# 15. Relational Access

随着 Idempotency Storage、Outbox Storage、Task Storage 等能力逐渐出现，一个新的问题变得明显：

> 每个技术组件是否应该自己维护一套 JDBC 代码？

当前答案是：

> **不应该。**

因此开始建设：

```text
Relational Access Component
```

它位于：

```text
Idempotency / Outbox / Task / Other Components
                   ↓
            Relational Access
                   ↓
            JDBC / DataSource
                   ↓
          MySQL / PostgreSQL
```

第一阶段保持轻量：

```text
update
query
execute
SqlStatement
RowMapper
统一参数绑定
统一异常转换
```

暂时不试图做：

```text
完整 ORM
Hibernate 替代品
MyBatis 替代品
复杂 DSL
自动实体映射平台
```

目标是：

> **建立技术组件使用关系型数据库时统一、稳定、可治理的最低层访问能力。**

---

# 16. Relational Access 与 Transaction 的区别

这两个组件必须严格分开。

```text
Relational Access
=
怎么执行 SQL

Transaction
=
这一组操作什么时候 Commit / Rollback
```

因此：

```text
Transaction
      ↓
Idempotency
      ↓
Relational Access
      ↓
DataSource
```

是合理关系。

而不是：

```text
Relational Access
自己偷偷开启事务
```

这条边界对后续 Outbox、幂等 Storage 等能力非常重要。

---

# 17. Concurrency

Concurrency Component 解决业务系统中常见的异步执行失控问题。

目标包括：

```text
统一线程池
统一任务提交
统一任务生命周期
统一超时
统一取消
统一 Fallback
统一 Metrics
```

而不是让业务系统不断出现：

```java
Executors.newFixedThreadPool(...);

CompletableFuture
        .supplyAsync(...)
        .thenApply(...)
        .exceptionally(...);
```

之后再由每个业务团队自己解决：

```text
线程池满了怎么办？
任务超时怎么办？
线程上下文怎么办？
如何关闭？
如何监控？
```

---

# 18. Foundation

Foundation 是所有技术组件最低层基础能力。

当前主要包括：

```text
foundation-core
foundation-time
foundation-id
foundation-error
foundation-serialization-jackson
foundation-json
foundation-validation
foundation-test
```

设计原则：

```text
小
稳定
通用
无业务语义
低依赖
```

Foundation 可以被所有技术组件使用。

Foundation 不依赖：

```text
Message
Idempotency
Lock
Retry
Transaction
```

等上层组件。

---

# 19. Cache

Cache 已有：

```text
Caffeine
+
Redis
```

多级缓存实现基础。

长期目标包括：

* Local Cache
* Distributed Cache
* 多级缓存
* TTL
* 本地失效
* 防穿透
* 防击穿
* 防雪崩
* Cache Metrics

当前不是主要建设线，后续会按照当前统一组件规范重新整理。

---

# 20. Observability

当前多个组件已经分别出现：

```text
Metrics
Events
MDC
Trace Context
Health
```

这些能力未来需要逐步统一到：

```text
Observability Component
```

它不会重新实现 SkyWalking。

推荐关系：

```text
Business / Components
          ↓
Iron Observability Abstraction
          ↓
Micrometer / OpenTelemetry
          ↓
Prometheus / Grafana / SkyWalking / Other Backend
```

因此业务和基础组件不会直接绑定单一 APM 平台。

---

# 21. Governance

服务治理长期覆盖：

```text
Timeout
Retry
Rate Limit
Circuit Breaker
Bulkhead
Fallback
```

Retry 已经作为独立可靠执行组件建设。

其他能力后续由 Governance Component 继续统一。

需要特别注意：

```text
Retry ≠ Rate Limit
Retry ≠ Circuit Breaker
Retry ≠ Bulkhead
```

这些能力可以组合，但职责不同。

---

# 22. Task / Distributed Execution

未来 Task Component 主要负责：

```text
长周期任务
超时扫描
任务恢复
Claim
Lease
分布式抢占
持久化 Retry
批处理
补偿任务
```

这里需要区分：

### Scheduler

回答：

> 什么时候开始执行？

### Retry

回答：

> 失败以后是否再次执行？

### Idempotency

回答：

> 重复执行是否安全？

### Claim / Lease

回答：

> 多节点环境下当前是谁负责执行？

这些概念不会合并成一个大组件。

---

# 23. Consistency / Outbox

下一阶段 Message Component 还将继续进入：

```text
Outbox
```

以及更完整的一致性能力。

未来 Consistency Component 可能包括：

```text
Outbox
Local Message Table
Status Check
Compensation
Dead Record
Manual Replay
```

但必须区分：

```text
Retry
=
重新执行原操作

Compensation
=
执行另一个动作修正系统状态
```

例如：

```text
调用支付系统网络失败
→ Retry

扣款已经成功，但订单创建最终失败
→ Compensation / Refund
```

---

# 24. Sharding

当前没有计划直接自研完整分库分表中间件。

更合理的方向是：

```text
先完成 Shard-Ready 数据模型
        ↓
明确 Shard Key / Scan Bucket
        ↓
建立数据路由抽象
        ↓
再决定是否接入 ShardingSphere
或业务自定义 Router
```

未来可能逐渐形成：

```text
ShardKey
RouteContext
ShardRouter
RouteResult
DatabaseRouter
TableRouter
```

底层可以适配：

```text
ShardingSphere
Hash Router
Range Router
Custom Router
```

但当前不会为了“未来可能分表”而过早把所有组件复杂化。

---

# 25. 当前组件组合关系

目前整体组件关系可以简单理解为：

```text
                 Foundation
                     │
        ┌────────────┼─────────────┐
        │            │             │
        ↓            ↓             ↓
 Concurrency       Retry     Relational Access
                     │             │
                     ↓             │
                Transaction        │
                     │             │
                     ↓             │
                Idempotency ───────┘
                     │
             ┌───────┴───────┐
             ↓               ↓
           Message      Distributed Lock
             │
       Reliable Consume
             │
             ↓
      Outbox / Task / Consistency
```

Observability 未来作为横向能力覆盖所有组件：

```text
Metrics
Trace
Events
Health
Logs
```

---

# 26. 推荐使用原则

## 原则一：只引入真正需要的组件

不要因为项目里有所有组件，就一次性全部依赖。

例如普通接口服务可能只需要：

```text
Foundation
+
Idempotency
```

消息消费者可能需要：

```text
Message
+
Idempotency
+
Transaction
+
Retry
```

---

## 原则二：优先依赖公开 API / Starter

业务代码尽量面对：

```text
xxx-api
```

或者：

```text
xxx-spring-boot-starter
```

而不是直接依赖：

```text
xxx-core
xxx-provider-redis
xxx-provider-kafka
```

内部实现。

---

## 原则三：基础设施由 Provider 适配

例如：

```text
DistributedLock API
        ↓
Lock SPI
        ↓
Redis Provider
```

未来可以增加：

```text
Etcd Provider
```

但调用者 API 尽量保持不变。

---

## 原则四：组合优先于重新实现

例如 Message Component 需要 Retry 时：

```text
Message
↓
Retry API
```

而不是在 Message Core 中重新实现一套 Retry Engine。

同样：

```text
Message
↓
Idempotency
```

而不是 Message Component 自己维护独立幂等状态机。

---

## 原则五：组件负责技术语义，业务负责业务语义

例如：

```text
Idempotency
```

可以知道：

```text
PROCESSING
SUCCESS
FAILED
DISCARDED
```

但不应该知道：

```text
ORDER_PAID
COUPON_SENT
ACCOUNT_FROZEN
```

后者仍属于业务领域。

---

# 27. 当前技术基线

当前长期技术基线：

```text
Java 17
Spring Boot 3
Maven Multi Module
```

主要基础设施方向包括：

```text
Redis
MySQL
PostgreSQL

Kafka
Pulsar
RocketMQ

Prometheus
Grafana
OpenTelemetry
SkyWalking

XXL-Job
```

成熟基础设施不会全部重新实现。

---

# 28. 当前 Roadmap

截至目前，整体路线已经从最初的“逐个造组件”转向：

> **把核心组件组合成真正可靠的业务执行链路。**

---

## Phase 1：基础执行底座

```text
Foundation
Concurrency
Retry
Transaction
Distributed Lock
```

当前：

> **主体能力已经形成。**

---

## Phase 2：幂等与消息可靠性

```text
Idempotency
Message
Reliable Consume
Transaction Integration
```

当前：

> **v1 主体能力已经形成，正在继续加强 Storage 与可靠性能力。**

---

## Phase 3：Storage & Consistency

当前新的重点方向：

```text
Relational Access V1
        ↓
Idempotency Shard-Ready Storage
        ↓
Message Outbox
        ↓
Task / Timeout Recovery
        ↓
Consistency
```

---

## Phase 4：Governance & Observability

后续统一：

```text
Metrics
Trace
Health
Rate Limit
Circuit Breaker
Bulkhead
Dynamic Configuration
```

---

## Phase 5：Data Governance

在真实数据规模需要时继续：

```text
Shard Routing
Read / Write Routing
SQL Governance
Database Routing
Table Routing
```

---

# 29. 当前主建设路线

当前不再把分布式锁作为主要开发线。

Redis Distributed Lock 主体已经形成。

现在更合理的路线是：

```text
Relational Access V1 收口
        ↓
Idempotency v2
Shard-Ready Storage
        ↓
Message v2
Outbox / Reliability
        ↓
Task / Timeout Scanner
        ↓
Consistency & Compensation
        ↓
Governance / Observability
        ↓
Sharding Routing
```

其中 Relational Access 的意义非常重要：

> 它不是 ORM，而是后续 Idempotency Storage、Outbox、Task Storage 等技术组件共同使用的关系型数据访问底座。

---

# 30. 文档体系

项目维护两类文档。

## 使用者文档

根目录：

```text
README.md
```

主要回答：

```text
项目是什么？
有哪些组件？
解决什么问题？
什么时候应该使用？
组件如何组合？
当前哪些能力可以使用？
```

---

## 组件设计与研发文档

位于：

```text
docs/
```

主要记录：

* 架构设计
* API / Core / SPI / Provider
* 类职责
* 状态机
* 时序图
* 组件图
* 关键决策
* 当前开发进度
* 后续扩展点

---

# 31. 项目设计理念

`iron-components-java` 不追求：

> 所有技术能力全部自己实现。

更关注：

> **如何把成熟基础设施转换成业务系统真正容易使用、拥有明确边界、能够治理、能够观察、能够替换并且能够组合的技术能力。**

典型结构：

```text
Stable API
    ↓
Core
    ↓
SPI
    ↓
Provider
    ↓
Infrastructure
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

消息：

```text
Message API
      ↓
Message Core
      ↓
MessageProvider
      ↓
Kafka / Pulsar / RocketMQ
```

数据访问：

```text
Technical Component
        ↓
Relational Access
        ↓
JDBC / DataSource
        ↓
MySQL / PostgreSQL
```

---

# 32. 最终目标

最终希望形成这样一套可组合的 Java 基础能力：

```text
Foundation
    +
Concurrency
    +
Retry
    +
Transaction
    +
Relational Access
    +
Idempotency
    +
Distributed Lock
    +
Message
    +
Cache
    +
Task
    +
Consistency
    +
Observability
    +
Governance
```

让业务系统更多关注：

```text
订单
支付
营销
账户
客户
清结算
风控
```

而不是让每个业务团队重新实现：

```text
线程池
Retry
Transaction Template
JDBC Storage
Idempotency
Distributed Lock
MQ Adapter
ACK
Outbox
Task Recovery
Trace
Metrics
```

最终希望得到的不是一堆互不相关的工具类，而是：

> **一套边界清晰、能够组合、能够实际服务业务系统，并且可以长期持续演进的 Java 技术组件底座。**
