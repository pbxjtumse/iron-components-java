# 存储扩容开发计划：幂等存储、ShardingSphere 接入与迁移

> 文档状态：开发规划，待实施；不代表中间件适配或在线迁移已经完成。  
> 整理日期：2026-09-17。  
> 代码基线：`master` 的 [3358ef6](https://github.com/pbxjtumse/iron-components-java/commit/3358ef602252963fd05eda957bbc7ae7709686e1)，已合入 route-aware 幂等存储。  
> 阅读对象：准备继续开发 Storage Routing、Relational Access 和 JDBC 幂等存储的开发者。

## 1. 本轮要完成什么

本轮的主候选方案是：**新目标集群 + ShardingSphere 数据迁移 + 受控流量切换**。先让现有幂等记录具备可迁移性，再验证中间件访问，最后演练扩容。

目标是：旧集群迁移到采用新分片规则的目标集群后，业务记录和对应幂等记录仍然共同落库，业务 SQL 与成功状态更新继续使用同一目标库事务，重复请求仍然能够识别历史执行结果。

| 事项 | 第一版建议 |
| --- | --- |
| 源端访问 | 保留现有 `DIRECT_DATASOURCE` 模式作为基线 |
| 目标存储 | 准备一套全新的数据库集群 |
| 数据搬迁 | 使用 ShardingSphere 提供的存量迁移、增量同步与校验能力 |
| 首轮目标访问 | 先通过 ShardingSphere-Proxy 验证逻辑表访问，后续单独验证 JDBC 接入 |
| 切换方式 | 暂停相关写入、排空执行、追平并校验、切换应用、恢复服务 |
| 验证规模 | 先旧 2 库 × 2 表到新 4 库 × 2 表，再扩大到 10 库 × 10 表等目标规模 |
| Virtual Bucket | 作为独立演进方向，本轮不把它设为迁移前置条件 |

这里的“新集群”需要迁移期间额外的存储资源；它与在原集群上只增加几个节点、仅搬部分数据不同。正式采用此路线前，要在锁定的 ShardingSphere 版本上验证源库、目标规则、表结构和多源归并场景的支持情况。

## 2. 当前代码已经有什么

以下结论来自基线代码阅读，测试类存在不等于本次已经运行或通过了迁移验收。

| 当前代码 | 已有职责 | 本轮需要补齐的部分 |
| --- | --- | --- |
| `RouteContext`、`CompositeShardKey` | 表达逻辑表以及单字段、复合字段分片依据 | 稳定的持久化与恢复协议 |
| `StorageRoute` | 保存输入上下文、分片结果、物理位置和模式 | 中间件模式的实际接入行为 |
| `StorageRouteAwareIdempotencyExecutor` | 复用业务路由，或根据 Factory 计算并绑定一次路由 | 将逻辑分片依据传到存储与恢复链路 |
| `StorageRoutingIdempotencyJdbcRouteResolver` | 复用业务分片结果，重新映射幂等表 | 按模式处理，避免把中间件路由重新组装成直连路由 |
| `RoutedJdbcIdempotencyRepository` | 根据 JDBC 路由选择并缓存单表 Repository | 传递每次调用的分片参数 |
| `JdbcIdempotencyRepository` | 幂等 SQL、历史状态判断、ownerToken/version 条件更新 | 分片依据落库及中间件所需的 SQL 路由条件 |
| `DefaultStorageRouteToSqlRouteBridge` | 将 Direct 路由转换为 `SqlRoute` | 当前明确拒绝非 Direct 模式，需要增加适配 |
| Spring 事务集成与 MySQL 测试 | 已有连接参与事务的实现和真实 MySQL 测试代码 | 完整幂等执行器、正式表结构、中间件及迁移场景验证 |

三个字段必须区分：

| 字段 | 当前含义 | 与扩容的关系 |
| --- | --- | --- |
| `idempotency_key` | 幂等身份的一部分 | 默认路由可使用它；业务按其他字段分片时，不能用它替代业务分片依据 |
| `route_key` | 业务路由元数据，例如租户、商户 | 当前代码明确声明它不承担存储分片职责，不能直接重新解释其含义 |
| `scan_bucket` | 一个物理分片内部的恢复扫描桶 | 不是数据库编号、物理表编号，也不是 Virtual Bucket |

当前 MySQL 表还有一个需要检查的点：主键 `id` 为 `AUTO_INCREMENT`。改变规则或多源归并时，要验证不同源表的 ID 是否会进入同一目标表并发生冲突；不能假定每表自增 ID 全局唯一。

## 3. 七类开发工作总览

| 编号 | 分类 | 主要模块 | 完成后获得的能力 |
| --- | --- | --- | --- |
| A | 存储模型与表结构 | `idempotent-api`、`idempotent-provider-jdbc` | 记录保存以后仍有足够的逻辑分片依据 |
| B | 路由信息传递 | `idempotent-integration-storage-routing`、Storage Routing 相关实现 | 在线执行、重试和恢复使用一致的逻辑分片依据 |
| C | 中间件适配 | `storage-routing-integration` | 逻辑表、逻辑数据源能够正确接入 JDBC/Proxy |
| D | Repository SQL | `idempotent-provider-jdbc` | 所有幂等点查和写入准确定位目标分片 |
| E | 自动装配与事务 | 各 starter、Spring 事务集成 | 模式选择、数据源注册与本地事务正确协作 |
| F | 迁移配置与切换控制 | 部署脚本、业务应用、演示工程 | 能执行迁移、暂停写入、切换和恢复 |
| G | 集成测试 | 模块测试与迁移验证工程 | 有证据证明扩容后路由、幂等、事务与恢复仍然正确 |

## 4. 按代码位置展开待办

### A. 存储模型与表结构

**要解决的问题：迁移程序只有数据库记录，不能读取原执行线程中的 RouteContext。**

例如订单按 `userId=1001` 分片，幂等 key 是 `pay:order:9001`。只保存幂等 key，无法推导订单使用的 userId；只保存旧物理分片号，也未必能够在新规则下重新分片。

| 代码位置 | 开发动作 |
| --- | --- |
| `schema-mysql.sql` | 设计持久化分片依据，建议字段暂名 `storage_routing_key`；确定类型、长度、空值、索引及编码约束 |
| `IdempotencyStorageContext` | 评估增加技术中立的逻辑路由信息，以便随存储请求跨层传递 |
| `IdempotencyRecord` | 读取持久记录时取得逻辑分片依据 |
| `IdempotencyRecoveryCandidate` 及恢复请求转换 | 将必要信息带到异步恢复执行，避免退回不匹配的默认路由 |
| 数据库升级脚本 | 对已有数据提供结构升级、回填和缺失数据校验 |

`storage_routing_key` 只是建议名称，字段与 Java 类型要在实现前定稿。幂等 API 只表达必要的存储元数据；`CompositeShardKey` 的转换由 integration 承担，不要求用户重新传递此前删除的 `IdempotencyRequest.shardKey`。

编码协议需要保留字段名、类型、顺序和值，并有版本信息。现有 `CompositeShardKey.canonicalForm()` 提供编码，尚需补充或明确恢复方式。单字段当前按值文本哈希，多字段按 canonicalForm 哈希；持久化编解码不能悄悄改变这套输入规则。详见 [现有路由模型](03-storage-route-model.md)。

幂等身份与分片依据的契约也要写清楚：同一个 `(storeName, namespace, idempotencyKey)` 在首次调用、重试和恢复时应使用一致的逻辑分片依据。单个物理表的唯一索引不能提供跨库全局唯一性，也不能靠把分片字段加进唯一索引来掩盖路由漂移。

- [ ] 定稿逻辑分片信息的模型、编码及持久化位置。
- [ ] 明确 `route_key`、`scan_bucket` 和存储分片依据的不同职责。
- [ ] 完成插入、读取、记录快照和恢复候选的信息传递。
- [ ] 对已有记录提供回填办法；不能恢复分片依据的记录需明确处置，不能猜测。
- [ ] 验证多源归并的主键、唯一键冲突；必要时在迁移前完成主键方案调整。
- [ ] 源端结构与数据准备完成后再启动迁移，避免把结构改造混入迁移过程。

### B. 路由信息传递

**要解决的问题：在线执行使用的逻辑分片依据，要完整进入存储链路，并能在恢复时重建。**

| 现有类 | 开发动作 |
| --- | --- |
| `IdempotencyRouteContextFactory`、默认实现 | 固定默认分片依据的生成规则，覆盖执行与恢复入口 |
| `StorageRouteAwareIdempotencyExecutor` | 复用或创建路由时确定本次调用的逻辑分片依据，并传给存储请求 |
| `StorageRoutingIdempotencyJdbcRouteResolver` | 分别处理 Direct 和中间件模式，保留逻辑分片信息 |
| 新增的编解码实现 | 完成类型化分片键与技术中立存储信息之间的转换 |

继续保留当前两个路由来源：已有业务路由时复用它；没有业务路由时采用默认幂等 key 规则。业务方要保证重试沿用同一条调用路径，避免首次按业务键、重试却按幂等 key 路由。

当前 resolver 的 `remap()`、`directIdempotencyRoute()` 通过默认 Builder 组装 Direct 结果。接入中间件时，需要显式按模式处理，不能仅更换 bridge 后仍沿用这些直连分支。

- [ ] 保持一次完整执行中的逻辑分片依据稳定。
- [ ] 默认路由与外层业务路由分别验证，避免二次哈希导致业务、幂等分离。
- [ ] 恢复转换携带持久化的分片依据；缺少必要信息时给出明确错误。
- [ ] 编解码后单字段、复合字段的既有落点保持一致。
- [ ] 验证嵌套作用域、异常退出和上下文清理。

### C. 中间件适配

**要解决的问题：Direct 使用物理表，中间件使用逻辑表，两者的执行语义需要明确区分。**

| 模式 | SQL 使用的表名 | JDBC 使用的数据源 |
| --- | --- | --- |
| `DIRECT_DATASOURCE` | 如 `iron_idempotency_record_07` 的物理表名 | 目标物理库的数据源 |
| `SHARDINGSPHERE_JDBC` | `iron_idempotency_record` 逻辑表名 | ShardingSphere 提供的逻辑 DataSource |
| `PROXY` | `iron_idempotency_record` 逻辑表名 | 连接 Proxy 的 DataSource |

在 `storage-routing-integration` 下增加对应适配，复用 `StorageRouteToSqlRouteBridge` 契约。是否新增独立 Maven 模块，按真实依赖决定；`storage-routing-integration-shardingsphere` 是可选的计划名称，不是本轮已经存在的模块。

逻辑数据源的选择放在适配配置中；`PhysicalStorageLocation` 保持物理位置语义。中间件负责物理库表选择，应用不提前拼接一次物理表名再交给它重复路由。

- [ ] 实现首轮 Proxy 逻辑表与逻辑数据源桥接。
- [ ] 保留 Direct 行为，覆盖模式不匹配和配置缺失的错误。
- [ ] 为业务表与幂等表配置能够共同落库的目标规则。
- [ ] 目标迁移写入与目标在线访问采用相同规则；新规则可以与源端不同。
- [ ] 首轮通过后，再验证 ShardingSphere-JDBC 接入及与迁移端配置的一致性。

### D. Repository SQL

**要解决的问题：中间件需要从 SQL 中取得足够的分片条件。**

| 现有类 | 开发动作 |
| --- | --- |
| `JdbcIdempotencyRepository` | INSERT 保存分片依据，读取并映射该字段；点查和状态写入携带需要的路由条件 |
| `RoutedJdbcIdempotencyRepository` | 将分片参数随本次方法调用交给实际 Repository |
| `IdempotencyJdbcRoute` | 明确表名是最终 SQL 使用的名字；Direct 为物理表，中间件为逻辑表 |
| JDBC 参数绑定与结果映射 | 同步调整参数顺序、空值处理、记录和候选对象构造 |

如果最终选定按 `storage_routing_key` 分片，成功状态更新的条件可表达为下面这样。此处是设计示意，字段尚未加入基线代码：

```sql
WHERE storage_routing_key = ? AND store_name = ? AND namespace = ? AND idempotency_key = ?
  AND status = ? AND owner_token = ? AND version = ?
```

分片条件负责定位记录；ownerToken、version、状态条件继续负责幂等并发控制。不要因为增加路由条件而弱化现有 CAS 约束。

`RoutedJdbcIdempotencyRepository` 当前按 `IdempotencyJdbcRoute` 缓存 Repository。不要把每个请求的分片键加入缓存身份，导致每个业务 key 都创建新的 Repository；调用参数与可复用的执行目标需要分开。

- [ ] 覆盖 `tryAcquire`、`find`、`tryRecover`、`markSuccess`、`markFailed`、`markDiscarded`。
- [ ] 检查内部窗口更新、读取锁定等辅助 SQL，避免只改公共方法中的部分语句。
- [ ] 更新正式 DDL、结果映射与 SQL 参数绑定。
- [ ] 设计中间件模式下的恢复扫描范围与分页；点访问和扫描不能混用同一假设。
- [ ] 保留 ownerToken/version、窗口和恢复状态语义，并验证 Direct 模式回归。

### E. 自动装配与事务

**要解决的问题：运行模式、数据源、Repository 与事务管理器能够正确组合。**

| 代码位置 | 开发动作 |
| --- | --- |
| `storage-routing-starter` | 装配模式对应的 bridge/adapter 与必要配置 |
| `idempotent-starter` | 装配 RouteResolver、Repository 和 JDBC 执行管理器 |
| `relational-starter` | 注册目标逻辑数据源和连接提供者 |
| `SpringTransactionJdbcExecutionManager` 等事务集成 | 验证幂等 SQL 参与业务事务时使用同一事务资源 |
| `SpringTransactionAwareConnectionProvider` 及测试 | 验证业务 SQL、Relational SQL 和中间件连接的协作 |

验收目标是业务 SQL 与 `markSuccess` 在同一目标库事务内共同提交或回滚。接入中间件后，要验证实际 SQL 落点和真实回滚行为，不能仅凭 Java 层 Connection 对象相同作结论。

抢占事务、业务事务和失败记录事务的边界仍按现有设计处理。若抢占已经独立提交，业务事务回滚后仍可能保留幂等记录；不能把“失败后所有幂等记录消失”当作正确性标准。

- [ ] 模式与装配选择一致，缺少必要配置时明确报错。
- [ ] 数据源选择在正确的事务边界内完成，避免事务绑定后再切换目标。
- [ ] 验证业务与成功状态共同提交、共同回滚。
- [ ] 验证状态更新失败、业务异常、路由不匹配等失败路径。
- [ ] 核心状态机继续处理幂等领域语义，不承接扩容作业调度。

### F. 迁移配置与切换控制

**要解决的问题：把官方迁移能力接到应用的实际发布、停写和恢复流程中。**

| 建议交付物 | 内容 | 归属 |
| --- | --- | --- |
| 目标库初始化 SQL | 数据库、表、索引与权限准备 | 部署脚本 |
| ShardingSphere 配置 | 数据源、逻辑表、分片规则、迁移参数 | 部署配置 |
| DistSQL 脚本 | 源端注册、任务启动、进度查看、校验与完成 | 迁移操作脚本 |
| 维护控制与执行计数 | 暂停新执行、统计并排空正在运行的完整调用 | 应用外围集成，先在 Demo 验证 |
| 消费与调度控制 | 暂停 MQ 消费、恢复扫描和定时任务，恢复后按原语义处理 | 业务应用/任务系统 |
| 切流与回退手册 | 旧写阻断、连接切换、验证、恢复和回退边界 | 运维文档 |

可将脚本集中到后续新增的 `scripts/storage-migration/`；目录名称属于建议。本次规划文档不代表这些脚本已经存在。

第一版操作顺序：

1. 固定版本、列全源物理表，完成目标表规则与连通性验证。
2. 验证多张源物理表汇入目标逻辑表的支持情况、主键及唯一键冲突。
3. 开始存量迁移和增量同步；目标端暂不执行业务写入、恢复或清理任务。
4. 暂停相关的新业务执行、消费、恢复扫描和调度，等待完整幂等调用结束。
5. 对旧应用落实源端写入阻断，处理旧连接和滞后实例；仅更新应用配置不够。
6. 确认所有相关迁移任务追平停写后的位点，完成数据校验，再切换应用连接与配置。
7. 恢复目标访问，验证业务、重复请求与恢复任务，再进入观察和清理阶段。

排空要覆盖“抢占已完成、业务事务还没开始”等调用阶段，不能只统计数据库中的活跃事务。未完成的 PROCESSING、失败记录、ownerToken/version 和过期时间要按既有语义迁移；不能为了迁移把它们批量重置成可执行状态。

数据库记录校验之外，还要验证业务与幂等记录共同落库。CDC 追平和数据校验完成以前，不能让目标端业务读取不完整的数据并据此取得新的执行权。

回退边界：目标尚未接收业务写入时，可以在确认源端完整后取消切换；目标已经接收业务写入时，需要处理这部分新增数据，不能直接改回旧连接地址。官方 `ROLLBACK MIGRATION` 用于撤销迁移作业且会清理目标端，不是业务切流后的反向同步工具。

- [ ] 固定源端、目标端、Proxy/JDBC 的版本与配置基线。
- [ ] 准备必要权限、日志保留、容量、限流与监控配置。
- [ ] 验证多源物理表迁移、CDC 追平及校验的完整流程。
- [ ] 为所有写入来源定义暂停、排空、阻断和恢复动作。
- [ ] 将维护拒绝与业务成功、幂等冲突区分，明确客户端和消息消费者的重试行为。
- [ ] 演练旧节点未更新、在途调用、迁移中断以及切换失败。
- [ ] 形成有明确前置条件和回退边界的操作手册。

### G. 集成测试

**要解决的问题：使用真实数据库与完整组件调用，证明方案能够工作。**

基线已有 `SameShardMysqlSharedTransactionIntegrationTest`，使用简化表结构验证共同提交、回滚及错误分片后果。它是连接事务验证的起点，不能替代完整 IdempotencyExecutor + 正式 DDL + 中间件 + 迁移的验收。

| 测试类别 | 必须证明的结果 |
| --- | --- |
| 编解码与持久化 | 单字段、复合字段、类型、顺序、特殊字符能够保存和恢复，既有落点不变 |
| 完整幂等调用 | 正式 DDL、Executor、Repository 能覆盖抢占、执行、成功、失败和恢复 |
| 中间件点访问 | 所有需要单分片的点查和写入准确路由，业务与幂等共同落库 |
| 事务 | 业务与成功状态共同提交/回滚，错误路由有明确处理 |
| 迁移前后重试 | 迁移前成功的请求，在切换后能够回放结果，不重新执行业务 |
| 恢复 | 迁移后的异常记录仍能定位，owner/version 条件更新保持有效 |
| 切换异常 | 在途执行、旧节点和任务中断不会造成新旧两边同时取得执行权 |
| 数据完整性 | 记录内容、幂等状态、窗口与恢复元数据保持一致，主键无归并冲突 |

- [ ] 小规模演练使用真实 MySQL：旧 2 库 × 2 表到新 4 库 × 2 表。
- [ ] 使用正式组件表结构与完整执行入口，避免只用简化表证明整个流程。
- [ ] 完成上述用例，再扩大验证规模并记录源端负载、迁移耗时及停写窗口。
- [ ] 环境未配置而跳过的测试不能计入迁移验收通过。

## 5. 三批实施顺序

| 批次 | 范围 | 验收后进入下一批的条件 |
| --- | --- | --- |
| 第一批：记录可迁移 | A、B、D 中的信息落库与读取，以及对应 G 测试 | 数据库记录具有足够的逻辑分片依据，重试与恢复能一致重建，Direct 模式回归通过 |
| 第二批：中间件可访问 | C、D 的逻辑表和 SQL 条件、E，以及对应 G 测试 | 完整幂等流程通过目标逻辑表执行，实际共同落库与事务测试通过 |
| 第三批：迁移可切换 | F 与完整 G 演练 | 小规模全量/增量迁移、校验、停写、切流和失败处理全部有验证记录 |

建议首先完成第一批。第一批的目标可以直接描述为：**从“执行时知道怎么路由”，推进到“记录保存以后，仍然知道应该怎么路由”。**

完成一项后，更新本文件对应复选框，并记录代码提交、测试环境和验证结果。不要仅因新增枚举、配置或测试类，就把整批任务标成完成。

## 6. 实现前需要定稿的决定

| 决定 | 当前建议 | 需要验证的事项 |
| --- | --- | --- |
| 持久化分片依据 | 独立的存储路由信息，暂名 `storage_routing_key` | 编码、字段长度、索引及历史回填 |
| Java 模型放置 | 幂等 API 保存技术中立数据，integration 负责 CompositeShardKey 转换 | 在线请求、记录、恢复候选的完整传递 |
| 唯一性与主键 | 保留明确的幂等身份契约；检查自增主键归并风险 | 选定目标规则是否导致冲突，是否需迁移前升级 |
| 目标分片算法 | 使用目标端明确配置的规则 | 迁移写入与在线访问一致，业务表和幂等表共同落库 |
| 首轮访问形态 | Proxy，随后验证 JDBC 适配 | SQL、事务、规则同步及部署成本 |
| ShardingSphere 版本 | 锁定具体发布版本后验证 | 官方 current 文档和 master 文档不等于已发布版本的验收结果 |
| 切换窗口 | 允许受控暂停写入 | 完整调用排空、增量追平、最终校验耗时 |

## 7. 代码与官方资料索引

### 7.1 当前代码入口

- [StorageRoute 模型说明](03-storage-route-model.md)
- [CompositeShardKey](../../storage-routing-api/src/main/java/com/xjtu/iron/storage/routing/api/CompositeShardKey.java)
- [StorageRoute](../../storage-routing-api/src/main/java/com/xjtu/iron/storage/routing/api/StorageRoute.java)
- [Direct bridge](../../storage-routing-integration/storage-routing-integration-relational/src/main/java/com/xjtu/iron/storage/routing/integration/relational/DefaultStorageRouteToSqlRouteBridge.java)
- [路由装饰执行器](../../../idempotent-component/idempotent-integration/idempotent-integration-storage-routing/src/main/java/com/xjtu/iron/idempotent/integration/storage/routing/StorageRouteAwareIdempotencyExecutor.java)
- [幂等 JDBC 路由解析器](../../../idempotent-component/idempotent-integration/idempotent-integration-storage-routing/src/main/java/com/xjtu/iron/idempotent/integration/storage/routing/StorageRoutingIdempotencyJdbcRouteResolver.java)
- [逻辑存储上下文](../../../idempotent-component/idempotent-api/src/main/java/com/xjtu/iron/idempotent/api/storage/IdempotencyStorageContext.java)
- [RoutedJdbcIdempotencyRepository](../../../idempotent-component/idempotent-provider/idempotent-provider-jdbc/src/main/java/com/xjtu/iron/idempotent/provider/jdbc/repository/RoutedJdbcIdempotencyRepository.java)
- [JdbcIdempotencyRepository](../../../idempotent-component/idempotent-provider/idempotent-provider-jdbc/src/main/java/com/xjtu/iron/idempotent/provider/jdbc/repository/JdbcIdempotencyRepository.java)
- [MySQL 正式表结构](../../../idempotent-component/idempotent-provider/idempotent-provider-jdbc/src/main/resources/META-INF/iron-idempotency/jdbc/schema-mysql.sql)
- [现有 MySQL 事务集成测试](../../../relational-access-component/relational-integration/relational-integration-spring/src/test/java/com/xjtu/iron/relational/integration/spring/SameShardMysqlSharedTransactionIntegrationTest.java)

### 7.2 官方能力与本项目责任

ShardingSphere 提供存量迁移、增量同步和数据校验。官方原理说明仍要求控制源端写入，在同步完成后由使用方切换流量。因此，应用停写、旧写阻断、连接切换、幂等和事务验收属于本项目需要补齐的工作。[数据迁移原理](https://shardingsphere.apache.org/document/current/cn/reference/migration/)

官方迁移部署使用 Proxy 的 Cluster 模式；线程数、批次和限流参数应通过已有配置管理。[运行部署](https://shardingsphere.apache.org/document/current/cn/user-manual/shardingsphere-proxy/migration/build/)、[使用手册](https://shardingsphere.apache.org/document/current/cn/user-manual/shardingsphere-proxy/migration/usage/)

当前官方限制包含准备新的目标集群、目标 HINT 分片策略限制及迁移期间的结构约束。选定版本后要逐项验证，不将这些能力假定为所有版本、所有规则均适用。[迁移限制](https://shardingsphere.apache.org/document/current/en/features/migration/limitations/)

`COMMIT MIGRATION` 完成迁移作业；`ROLLBACK MIGRATION` 撤销作业并清理目标端。两者不能替代应用流量切换或目标端接收新业务写入后的数据回退方案。[COMMIT MIGRATION](https://shardingsphere.apache.org/document/current/cn/user-manual/shardingsphere-proxy/distsql/syntax/ral/migration/commit-migration/)、[ROLLBACK MIGRATION](https://shardingsphere.apache.org/document/current/cn/user-manual/shardingsphere-proxy/distsql/syntax/ral/migration/rollback-migration/)
