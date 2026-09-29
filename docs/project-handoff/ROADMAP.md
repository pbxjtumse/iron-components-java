# Migration Roadmap

Roadmap 按“先恢复可信构建，再扩展功能”排序。开始任务前应重新核对最新 master。

## P0：恢复可信验证基线

### 1. 对齐 POM 校验器

修改 `scripts/validate-poms.py`，使其符合当前 Maven 架构：

- 组件源码通过 `component/pom.xml` 继承内部 dependencyManagement；
- 组件根不导入 `component-bom`；
- 对外 BOM 只管理公开消费坐标，不要求覆盖所有内部实现模块；
- Demo Boot Plugin、直接依赖和显式内部版本分别校验，不混为一类。

验收：脚本只报告真实、可操作的问题。

### 2. 修复直接测试依赖

核验并补齐 `check-source-dependencies.py` 报告的四个测试依赖，然后执行受影响模块测试。

### 3. 建立全仓构建证据

在 JDK 17 + Maven 3.9.x 环境执行：

```bash
python3 scripts/validate-poms.py
python3 scripts/check-source-dependencies.py
mvn -U -DskipTests compile
mvn -U clean verify
```

保留 Surefire/Failsafe 报告和失败模块清单。

## P1：Storage Routing 与幂等同片事务

### 1. Direct 基线

- 证明 10×10 拓扑下路由稳定。
- 证明一次 execute/recover 只解析一次路由。
- 证明 Idempotency JDBC 与业务 Repository 复用同一路由和事务绑定 Connection。

### 2. ShardingSphere-JDBC

- 使用真实 ShardingSphere DataSource。
- SQL 显式携带分片列。
- 验证逻辑表、事务、回滚、重复请求和 Recovery。

### 3. ShardingSphere-Proxy

- 使用真实 Proxy 地址对应的普通 JDBC DataSource。
- 验证逻辑表路由、事务行为、异常映射和连接池配置。

注意：JDBC 和 Proxy Adapter 已经存在，本阶段不是重新创建 Adapter。

## P1：Message 可靠性闭环

- 本地编译 `MessageSendExecutor` 和 `DefaultReliableMessageSender`。
- 分别联调 Kafka、Pulsar、RocketMQ4 的成功、确定失败和 UNKNOWN。
- 固化 Provider 异常分类与 Retry 判定。
- 完成 Consume V4 中幂等与事务 Integration Adapter 的真实装配。
- 增加重复消息、业务回滚、ACK 失败和 Recovery 测试。

## P2：Distributed Lock 收口

- 验证 Redis Lua release/renew/check 的 owner token 语义。
- 验证 Core-managed 与 Redisson Provider-managed Watchdog。
- 验证 JDBC sequence fencing 与最终业务资源条件写入。
- 统一事件、指标和 Provider Capabilities 文档。

## P2：Cache / Governance / Observability

### Cache

- 多实例本地缓存失效通知。
- 分布式互斥加载。
- 动态 CacheSpec。
- 慢加载和降级指标。

### Governance

- 收口限流、隔离、超时、熔断的执行顺序。
- 增加 Runtime、Engine 和 Spring AOP 的集成测试。

### Observability

- 在现有 Trace/MDC/OTel 基础上定义统一 Metrics API。
- 统一组件事件标签。
- 后续再考虑使用真实 Trace 数据驱动链路动画和问题复现视图。

## P3：发布与业务模板

- 明确公共发布坐标并完善 `component-bom`。
- 增加消费者样例工程，证明业务项目无需继承组件 Parent。
- 发布组件版本并生成变更日志。
- 基于成熟组件建设订单、营销、消息消费等业务模板。
- 将文档和代码索引接入后续 AI 知识库。
