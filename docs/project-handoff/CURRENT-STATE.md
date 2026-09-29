# Current State Snapshot

## 1. 快照

| 项目 | 值 |
|---|---|
| 分支 | master |
| HEAD | `0ae78d402e68a34e4c4ac41f8a2a7c51c11a687d` |
| 日期 | 2026-09-29 |
| Java 源文件 | 1117 |
| 测试类（`*Test` / `*IT`） | 102 |
| Maven POM | 130 |
| Markdown 文档 | 159 |
| 技术组件 | 12 |

最近主线已经完成：

- ShardingSphere-JDBC Adapter 合入；
- ShardingSphere-Proxy Adapter 合入；
- Storage Routing 相关测试运行时依赖调整；
- 12 个组件的 L0-L4 / State 文档整理；
- Maven Parent、内部 dependencyManagement 和外部 BOM 的职责文档修正。

## 2. 本次只读验证结果

### Git

- `master` 与 `origin/master` 在克隆时一致。
- 工作开始前没有未提交修改。

### Maven

当前执行环境没有安装 `mvn`，所以本次无法证明 Reactor 编译和测试通过：

```text
/bin/bash: mvn: command not found
```

迁移到新账号后，必须在有 JDK 17 和 Maven 的环境重新运行：

```bash
mvn -version
mvn -U -DskipTests compile
mvn -U clean verify
```

### `scripts/validate-poms.py`

脚本当前退出码为 1，并报告 161 个错误。但其中大量结果来自校验规则与刚确立的 Maven 架构相冲突：

- 脚本仍要求组件根导入 `component-bom`；
- `component/MAVEN-ARCHITECTURE.md` 和当前 POM 已明确组件源码不再导入 BOM；
- 脚本要求所有内部组件依赖都出现在对外 BOM；
- 当前设计是内部版本由 `component/pom.xml` 管理，对外 BOM 只列公开消费坐标。

因此不能把 161 条全部当成 161 个独立工程缺陷。应该先修正校验器的模型，再重新分析剩余真实错误。

脚本同时暴露出可能需要单独核验的问题：

- 两个 Starter 测试依赖声明；
- 一些内部依赖仍显式写 `${project.version}`；
- Demo 的 Spring Boot Maven Plugin 判定规则可能过严，也可能存在真实缺失。

### `scripts/check-source-dependencies.py`

脚本退出码为 1，报告 4 个直接测试依赖缺失：

1. `idempotent-storage-routing-e2e` 使用 AssertJ，但 POM 未显式声明。
2. `idempotent-storage-routing-e2e` 使用 JUnit Jupiter，但 POM 未显式声明。
3. `transaction-demo-jpa` 使用 JUnit Jupiter，但 POM 未显式声明。
4. `transaction-demo-mybatis` 使用 JUnit Jupiter，但 POM 未显式声明。

这些结果应在 Maven Reactor 中验证后修复，不应通过放宽源码依赖检查来掩盖。

## 3. 当前最重要的事实校正

- 不要再把 ShardingSphere-Proxy Adapter 写成“后续新增”；代码已经存在。
- Storage Routing 的下一步是 SQL 分片列、真实 JDBC/Proxy 环境和同片事务 E2E，不是重新创建 Adapter。
- Message 普通收发已覆盖 Kafka、Pulsar、RocketMQ4；可靠发送仍处于工程验证阶段。
- Observability 已有 Web Filter、Trace Aspect 和 OTel 主线；统一 Metrics API 仍不是完成态。
- Message 消费幂等/事务已有 Integration Contract，但不能描述成所有真实 Adapter 都已闭环。

## 4. 已知环境和验证约束

- 历史本地开发环境使用 JDK 17、Maven 3.9.x、MySQL。
- 仓库中仍存在部分 H2 测试或 Demo 依赖；“移除 H2”是历史诉求，不是当前 master 已完成事实。
- 真实 Redis、三种 MQ、ShardingSphere-JDBC 和 Proxy 联调依赖外部环境，不能只凭单元测试宣称完成。
- 所有数据库地址、账号和 Token 都不应写进交接文件或提交到仓库。

## 5. 新账号接手后的第一轮验证

```bash
git status --short --branch
git log -1 --oneline
java -version
mvn -version
python3 scripts/validate-poms.py
python3 scripts/check-source-dependencies.py
mvn -pl component/storage-routing-component -am test
mvn -pl component/idempotent-component -am test
mvn -pl component/message-component -am test
mvn -U clean verify
```

先保存原始失败日志，再决定是代码、POM、测试环境还是校验脚本的问题。
