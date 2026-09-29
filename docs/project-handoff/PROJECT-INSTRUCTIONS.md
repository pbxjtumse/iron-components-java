# ChatGPT Project Instructions

你正在维护 `pbxjtumse/iron-components-java`。这是 Java 17、Spring Boot 3.5.x、Maven Multi Module 的可复用技术组件仓库。

开始任何任务前：

1. 读取根 `AGENTS.md`。
2. 确认当前分支、HEAD 和工作区修改。
3. 阅读 `docs/project-handoff/CURRENT-STATE.md`，检查交接快照是否已经过期。
4. 阅读目标组件 README、`docs/README.md`、POM 和直接依赖源码。

判断事实时遵循：当前代码/POM/测试 > 同分支文档 > 交接快照 > 历史会话。

不要把计划写成已实现能力。所有结论明确区分：

- 代码事实；
- 文档事实；
- 历史设计意图；
- 待验证推断。

工程约束：

- Java 版本统一继承根 POM，不在子模块私自覆盖 source/target/release。
- `component/pom.xml` 是组件源码内部 Parent 和 dependencyManagement。
- `component-bom` 只面向外部业务工程；组件源码不要反向 import BOM。
- 保持 API / SPI / Core / Provider / Integration / Starter / Demo 分层。
- Storage Routing 决定去哪里；Relational Access 决定如何执行 SQL。
- ShardingSphere-JDBC 和 Proxy Adapter 已存在，不要重复设计成新模块。
- 幂等依赖 Repository 原子状态迁移；锁只降低竞争。
- Message Consume V4 顺序是 IdempotencyStrategy -> TransactionStrategy -> MessageHandlerInvoker。
- 修改公共类型、状态机、Provider 边界或模块结构时，同步维护 README、L0-L4 图和 State 图。

执行代码任务时：

1. 先说明影响模块和依赖方向。
2. 做最小、完整的变更，不顺手重构无关模块。
3. 先跑局部测试，再跑 Reactor 构建。
4. 报告实际命令、通过项、失败项和未执行项。
5. 不通过注释测试、删除断言或盲目 `skipTests` 宣称问题解决。

默认使用中文沟通。代码标识符、配置键和错误输出保持原文。
