# ChatGPT / Codex 项目迁移入口

这组文件用于把 `iron-components-java` 的项目上下文迁移到另一个 ChatGPT 账号或新的 ChatGPT Project。

## 快照基线

- 仓库：`pbxjtumse/iron-components-java`
- 基线分支：`master`
- 基线提交：`0ae78d402e68a34e4c4ac41f8a2a7c51c11a687d`
- 提交主题：`docs: correct component dependency directions`
- 快照日期：2026-09-29

这些文件是交接快照，不替代源码。迁移后，目标账号必须优先检查当前分支和最新提交。

## 文件说明

| 文件 | 用途 |
|---|---|
| [`PROJECT-CONTEXT.md`](PROJECT-CONTEXT.md) | 项目目标、结构、组件全景和组合关系 |
| [`ARCHITECTURE-DECISIONS.md`](ARCHITECTURE-DECISIONS.md) | 已确认的架构决定和禁止回退的边界 |
| [`CURRENT-STATE.md`](CURRENT-STATE.md) | master 快照、验证结果和当前风险 |
| [`HISTORICAL-CONTEXT.md`](HISTORICAL-CONTEXT.md) | 从历史会话保留的设计意图、分支演进和未决事项 |
| [`ROADMAP.md`](ROADMAP.md) | 按优先级整理的后续工作 |
| [`PROJECT-INSTRUCTIONS.md`](PROJECT-INSTRUCTIONS.md) | 可直接粘贴到 ChatGPT Project Instructions 的内容 |
| [`../../AGENTS.md`](../../AGENTS.md) | 仓库内代码 Agent 的长期执行规则 |

## 导入到另一个 Pro 账号

1. 在 Pro 账号中创建 ChatGPT Project：`iron-components-java`。
2. 连接或上传当前 GitHub 仓库，确认默认分支为 `master`。
3. 将本目录中的 Markdown 文件加入 Project Sources。
4. 将 `PROJECT-INSTRUCTIONS.md` 内容粘贴到 Project Instructions。
5. 如果使用 ChatGPT 桌面端或 Codex，把仓库根目录设为项目主目录，使根 `AGENTS.md` 自动生效。
6. 在新项目的第一个会话中发送下面的启动提示词。

```text
请先读取仓库根 AGENTS.md、docs/project-handoff/ 下的全部文件、根 README.md、
component/DOCUMENTATION-INDEX.md 和目标组件 docs/README.md。

先不要修改代码。请核对当前分支和 HEAD，然后输出：
1. 当前组件全景；
2. 已实现、验证中、规划中的能力；
3. 交接快照与当前代码的差异；
4. 已确认的架构边界；
5. 构建与测试风险；
6. 最适合继续推进的三个任务。

所有结论都要区分：代码事实、文档事实、历史背景、待验证推断。
```

## 迁移验收

目标账号能正确回答以下问题，说明主要上下文已恢复：

- `component/pom.xml` 和 `component-bom` 分别负责什么？
- Storage Routing 与 Relational Access 的依赖方向是什么？
- ShardingSphere-JDBC 和 Proxy Adapter 是否已经存在？
- Idempotency 三段事务分别承担什么职责？
- Message Consume V4 的策略顺序是什么？
- 当前 POM 校验剩余哪些结构问题，哪些只是 Demo 可执行打包警告？
