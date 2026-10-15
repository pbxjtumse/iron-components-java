# Iron Components Java Agent Guide

本文件是整个仓库的默认工程指导。新会话依据当前工作树和相关文档恢复上下文，不假定自动拥有其他 ChatGPT / Codex 会话的内容。

## 事实、需求与设计依据

- 判断当前实现：检查当前工作分支的源码、POM、配置及相关测试。测试源码表达预期，只有实际执行结果才能证明相应范围已经验证。
- 判断目标行为：以本次用户明确需求和仍有效的已接受设计决策为依据；现有代码不自动代表正确设计。
- 组件文档用于说明职责、流程和约束；交接资料与历史会话只提供背景。不要把旧规划、未落地设计或旧验证结果描述为当前能力。
- 代码、文档、设计决策或本次需求冲突时，指出冲突及依据。可以根据证据判断的自行解决；无法判断且会改变关键业务语义的才向用户提问，并继续推进不依赖答案的工作。
- 确认文档过时的，修正文档；确认实现偏离要求的，修正代码并验证。不得仅为使文档与代码一致而改写已确定的业务要求。
- 区分“已实现”“已验证（注明范围）”“待实现”“待确认”；不要把 Agent 自己的假设写成用户已经确认的设计。

## 工程基线

- Java 17、Spring Boot 3.5.x、Maven Multi Module；具体依赖和插件版本以当前根 POM 为准。
- 根版本：`1.0.0-SNAPSHOT`。
- 应用样例采用 COLA 风格的 `client / adapter / app / domain / infrastructure / start` 分层。
- 可复用技术组件位于 `component/`，是仓库主线。
- 默认中文沟通和文档；代码标识符、配置键和错误输出保持原文。

## Maven 边界

- 根 `pom.xml`：全仓库聚合、公共第三方 BOM 和构建插件基线。
- `component/pom.xml`：组件源码的 Aggregator、Parent 和内部依赖版本目录。
- `component/component-bom/pom.xml`：面向外部业务工程发布的 BOM；组件源码不要反向导入它。
- 跨组件开发优先从仓库根目录使用 Reactor 和 `-am`。
- 不要在子模块重新声明 Java `source/target/release` 来绕开父 POM。
- 详细职责见 `component/MAVEN-ARCHITECTURE.md`。

## 组件分层

新增或修改模块时优先保持以下职责：

- `api`：稳定公共类型和业务入口。
- `spi`：Provider 或框架扩展契约。
- `core`：与中间件无关的编排和状态机。
- `provider-*`：Redis、MyBatis 等具体实现。
- `integration-*`：跨组件或框架桥接。
- `starter`：Spring Boot 自动装配。
- `demo` / `*-e2e`：示例和端到端验证，不作为公共依赖。

业务逻辑层优先依赖 API，不直接绑定 Core、Provider 或中间件 SDK；应用装配可使用 Starter。基础设施适配、Integration、Starter 和验证模块可以按职责依赖具体实现，不因这一规则删除必要依赖。已有组件若有明确的特殊分层，先核对其设计，不机械新增或拆分模块。

## 核心设计约束

- Storage Routing 决定“去哪里”；Relational Access 提供 MyBatis Mapper 访问与事务资源一致性检查，不计算分片，也不接管各组件的业务 SQL / CRUD。
- Relational Access 的事务执行委托给 Spring / MyBatis 及 Transaction 集成；不自行手写 Connection 的 begin/commit/rollback，不承诺跨数据库原子性。
- ShardingSphere 桥接不解析或改写 SQL，也不复制 ShardingSphere 规则；Repository SQL 显式携带规则要求的分片列。
- 幂等正确性来自 Repository 原子状态迁移，不来自分布式锁。
- Message Consume V4 的约定顺序是 `IdempotencyStrategy -> TransactionStrategy -> MessageHandlerInvoker`；调整顺序需要明确设计理由及兼容性影响。
- 分布式锁的 fencing token 必须由最终业务资源参与条件写入，才能防止旧 Owner 覆盖。

设计理由见 `docs/project-handoff/ARCHITECTURE-DECISIONS.md`。各项能力的实现与验证状态应核对当前组件代码和文档，不以本文件代替进度报告。

## 设计与实施流程

1. 先确认当前分支、已有工作区修改、目标模块和任务范围；涉及依赖时核对父 POM 与直接依赖。
2. 阅读任务所需的代码、目标组件 README、详细设计和相关决策。不要求每次任务读取整个仓库或全部交接资料。
3. 新增行为、修改公共 API 或调整结构前，检查设计是否覆盖目标、输入输出、状态变化、约束、异常处理和验收标准；按任务补充事务、并发、恢复与生命周期等相关语义。
4. 区分已确定要求、当前实现和合理假设。常规实现细节自行决定；关键设计缺口按上文冲突规则处理，不擅自扩大任务范围。
5. 实施范围内的修改。公共 API、状态枚举、Provider 边界或模块结构变化时，同步更新相关文档和图；重要取舍记录背景、理由、代价与状态。
6. 从需求和验收标准推导验证场景，优先执行目标模块测试，再按影响范围验证 Reactor。不要只复述实现来证明实现正确，也不要修改断言以掩盖缺陷。
7. 报告修改内容、理由、实际执行命令、通过项、失败项和未验证项；保留影响结论的假设。静态检查或编译通过不等于功能测试、联调或生产验收通过。

## 验证命令

按任务选择适用命令，不要求每次全部执行：

```bash
python3 scripts/validate-poms.py
python3 scripts/check-source-dependencies.py
mvn -pl component/<component-name> -am test
mvn -pl component/<component-name> -am compile
mvn clean verify
```

- 纯文档变更优先检查引用、图与变更差异；影响代码、依赖或运行配置时再执行相应构建和测试。
- `-U` 仅在需要更新远端快照或重试依赖解析时使用；跳过测试的构建只能用于诊断或编译检查，不能作为测试通过的证据。
- 验证受环境、网络或依赖限制时，记录实际阻塞和未验证范围，不虚构通过结果。
- 脚本用法见 `scripts/README.md`。旧快照中的构建错误、模块数量和机器环境不作为当前基线。

## 文档与图

- 项目入口见根 `README.md`，组件导航见 `component/DOCUMENTATION-INDEX.md`；具体设计放在相应组件文档，重要跨组件决策放在架构决策文档。
- 图示遵循 `component/DOCUMENTATION-STANDARD.md`：L0 外部总览、L1 Public API 主流程、L2 异常和重要分支、L3 Core 内部协作、L4 Provider / Spring / Middleware 集成。
- 描述现状的图以当前工作分支的真实类型、方法和枚举为依据；目标设计或推导模型明确标注，推导模型使用 `[DOC MODEL]`。
- 通用工作规则放在本文件；不要把聊天原文、临时日志、容量统计或组件详细设计堆入规则文件。
- 需要保存状态快照时，注明日期、分支、提交和验证范围；不能从旧快照推导当前完成度。
- 文档与代码冲突按“事实、需求与设计依据”处理。删除重复或过时文档前，保留独特的有效信息，并同步修复入口和引用。

## 安全与变更范围

- 不提交密码、Token、真实生产连接串或个人数据。
- 不因为构建失败而删除测试、注释断言或跳过关键模块。
- 不直接修改 `master`；使用任务分支并保持提交范围单一。
- 保留用户已有修改，不清理与当前任务无关的工作区内容。
