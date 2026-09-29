# Iron Components Java Agent Guide

本文件对整个仓库生效，供 ChatGPT、Codex 和其他代码 Agent 在新会话中恢复稳定的工程约束。

## 事实优先级

发生冲突时按以下顺序判断：

1. 当前工作分支的源码、POM、测试和配置。
2. 与代码同提交维护的组件文档。
3. `docs/project-handoff/` 中的快照和历史背景。
4. 旧会话中的计划、口头结论或尚未落地的设计。

不要把历史规划描述成已实现能力。无法从当前代码确认时，明确标记为“待验证”。

## 工程基线

- Java 17。
- Spring Boot 3.5.x。
- Maven Multi Module。
- 根版本：`1.0.0-SNAPSHOT`。
- 应用样例采用 COLA 风格的 `client / adapter / app / domain / infrastructure / start` 分层。
- 可复用技术组件位于 `component/`，它们是仓库的主线。

## Maven 边界

- 根 `pom.xml`：全仓库聚合、公共第三方 BOM 和构建插件基线。
- `component/pom.xml`：组件源码的 Aggregator、Parent 和内部依赖版本目录。
- `component/component-bom/pom.xml`：只面向外部业务工程发布的 BOM。
- 组件源码不要反向导入 `component-bom`。
- 跨组件开发优先从仓库根目录使用 Reactor 和 `-am`。
- 不要在子模块重新声明 Java `source/target/release` 来绕开父 POM。

## 组件分层

新增或修改模块时优先保持以下职责：

- `api`：稳定公共类型和业务入口。
- `spi`：Provider 或框架扩展契约。
- `core`：与中间件无关的编排和状态机。
- `provider-*`：Redis、JDBC 等具体实现。
- `integration-*`：跨组件或框架桥接。
- `starter`：Spring Boot 自动装配。
- `demo` / `*-e2e`：示例和端到端验证，不作为公共依赖。

业务代码优先依赖 API 或 Starter，不直接依赖 Core、Provider 或中间件 SDK。

## 已确定的重要边界

- Storage Routing 决定“去哪里”；Relational Access 决定“如何执行 SQL”。
- Relational Access 不计算分片，也不管理业务事务的 begin/commit/rollback。
- ShardingSphere-JDBC 和 ShardingSphere-Proxy Adapter 已存在；它们不解析或改写 SQL，也不复制 ShardingSphere 规则。
- 幂等正确性来自 Repository 原子状态迁移，不来自分布式锁。
- Message Consume V4 的真实顺序是 `IdempotencyStrategy -> TransactionStrategy -> MessageHandlerInvoker`。
- 分布式锁的 fencing token 必须由最终业务资源参与条件写入才真正防止旧 Owner 覆盖。

完整决策见 `docs/project-handoff/ARCHITECTURE-DECISIONS.md`。

## 修改流程

1. 先确认当前分支、目标模块、父 POM 和直接依赖。
2. 阅读目标组件的 `README.md` 或 `readme.md` 以及 `docs/README.md`。
3. 修改公共 API、状态枚举、Provider 边界或模块结构时，同步更新文档和图。
4. 优先执行目标模块局部测试，再执行受影响的 Reactor 构建。
5. 报告实际执行过的命令、通过项、失败项和未验证项；不要把“未执行”写成“已通过”。

常用命令：

```bash
python3 scripts/validate-poms.py
python3 scripts/check-source-dependencies.py
mvn -pl component/<component-name> -am test
mvn -U -DskipTests compile
mvn -U clean verify
```

当前校验基线与已知问题见 `docs/project-handoff/CURRENT-STATE.md`。POM 校验已经按 Parent / 内部 dependencyManagement / 对外 BOM 三层职责重写；先处理它报告的结构问题，再进入 Maven Reactor。

## 文档与图

- 图示遵循 `component/DOCUMENTATION-STANDARD.md`。
- L0：组件外部总览。
- L1：Public API 主流程。
- L2：异常和重要分支。
- L3：Core 内部协作。
- L4：Provider、Spring 和 Middleware 集成。
- 状态图优先使用真实枚举；推导模型必须标记 `[DOC MODEL]`。
- 文档与代码冲突时，以当前代码为准，并在同一个变更中修正文档。

## 安全与变更范围

- 不提交密码、Token、真实生产连接串或个人数据。
- 不因为构建失败而删除测试、注释断言或跳过关键模块。
- 不直接修改 `master`；使用任务分支并保持提交范围单一。
- 保留用户已有修改，不清理与当前任务无关的工作区内容。
