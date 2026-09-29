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

脚本已经重写为纯 Python 标准库实现，并明确区分：根聚合、组件内部 Parent / dependencyManagement、对外 BOM、源码直接依赖和 Maven 实际构建。

当前退出码为 1，剩余 **9 个可操作的结构问题**：

1. `transaction-component` 尚未继承 `component/pom.xml`。
2. `transaction-component` 仍覆盖 compiler release。
3. `distributed-lock-component` 仍反向导入 `component-bom`。
4. `idempotent-storage-routing-e2e` 对两个已管理内部依赖保留冗余版本。
5. `relational-spi` 对 `relational-api` 保留冗余版本。
6. `component/pom.xml` 缺少 `idempotent-integration-storage-routing` 管理项。
7. `component/pom.xml` 缺少 `relational-core` 管理项。
8. `component/pom.xml` 缺少 `relational-integration-spring` 管理项。

其中第 4 条对应两个具体依赖声明，所以脚本输出总数为 9、上面归并为 8 组；第 1、2 条通常可以在 Transaction Parent 收口时一起解决。

脚本另报告 **9 个警告**：九个带 `@SpringBootApplication` 的组件 Demo 没有启用 `spring-boot-maven-plugin`。这不会阻止普通编译，但不会生成可直接 `java -jar` 的 Boot 可执行包；需要明确 Demo 是仅供 IDE / `spring-boot:run` 使用，还是要求发布可执行包。

### `scripts/check-source-dependencies.py`

脚本已经修正两类误判：

- `spring-boot-starter-test` 可以提供其标准测试依赖组合；
- Lombok 的 `provided` scope 对主源码编译有效。

当前检查 130 个模块，退出码为 0，缺失声明为 0。

### 组件级脚本

- Foundation POM XML：通过，11 个 POM 可解析。
- Retry package layout：通过。
- Retry comment style：通过，检查 58 个 Java 文件。

完整脚本职责见 `scripts/README.md`。

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
bash scripts/build-first.sh
```

POM 静态校验归零后，再执行局部 E2E 和真实中间件联调。先保存原始失败日志，再决定是代码、POM、测试环境还是基础设施的问题。
