# Iron Components 文档与 UML 分层规范

本文统一 `component/*-component/docs` 的文档和图形结构，让读者可以从 L0 到 L4 逐层下钻，并保证图中的类、模块、状态与当前代码一致。

## 推荐目录

```text
docs/
├── README.md
└── diagrams/
    ├── component/00-component-overview.puml
    ├── sequence/
    │   ├── L0-overview.puml
    │   ├── L1-main-flow.puml
    │   ├── L2-scenario-flow.puml
    │   ├── L3-internal-flow.puml
    │   └── L4-integration-flow.puml
    └── state/00-full-state.puml
```

已有成熟图集不强制迁移目录；可以保留旧路径，并在 `docs/README.md` 中建立统一入口。

## L0-L4

| 层级 | 面向谁 | 重点 |
|---|---|---|
| L0 | 第一次了解组件的人 | 业务请求从哪里进入、组件解决什么、最终到哪里 |
| L1 | 业务接入者 | Public API 主链路和成功路径 |
| L2 | 排障/集成人员 | 重试、失败、降级、重复、超时等重要分支 |
| L3 | 组件维护者 | Core 内部类、Registry、Pipeline、StateMachine 协作 |
| L4 | Provider/框架集成人员 | Spring Starter、Redis/JDBC/MQ/OTel/ShardingSphere 等边界 |

L0-L4 表示阅读深度，不是版本号或成熟度。

## Component Diagram

只表达模块、核心职责与依赖方向。类级细节放到 L3 或单独 class diagram。

## State Diagram

只有真实存在状态、阶段、生命周期或明确代码枚举时才画状态图。

- 枚举状态必须使用当前代码真实枚举名。
- 推导生命周期必须标注 `[DOC MODEL]`。
- Provider 自己维护的状态机不能冒充 Iron Core 内部状态。

## 代码优先

```text
current source code
    ↓
current module structure
    ↓
docs text
    ↓
PlantUML
```

文档与代码冲突时，以当前默认分支代码为准。

## 图形要求

1. 类名、方法名、枚举值尽量直接引用代码。
2. Future/Optional 能力必须显式标注。
3. 一张图只回答一个主要问题。
4. State 图优先完整，Sequence 图优先分层。


## 7. Build-only 模块豁免

`component-bom`、纯 Maven aggregator 等没有运行时执行链路的模块不强制绘制 Sequence / State Diagram。它们应该维护依赖治理或 Maven 架构文档，而不是为了“图齐全”虚构运行时状态机。
