# 第一轮关系访问与缓存边界优化

本轮实施选择：修复已经复现的资源配对与缓存事件问题，将 Direct 公共资源装配放入 Relational Starter，保留组件自己的 SQL 与路由语义。

## 行为与约束

1. 创建 `SpringMyBatisAccess` 时，传入的 DataSource 必须与 SqlSessionFactory Environment 使用同一资源，事务工厂必须是 `SpringManagedTransactionFactory`。允许解包 `TransactionAwareDataSourceProxy`，其他连接池或路由代理按对象身份匹配，不能用相同 JDBC URL 代替资源一致性。
2. `execute` 无事务时保持普通执行；存在实际事务时必须绑定当前 Access 的 DataSource，且连接不能处于自动提交状态，错误资源在 Mapper 执行前失败。`executeInCurrentTransaction` 必须已有匹配事务；`executeInNewTransaction` 仍委托 TransactionExecutor 执行独立事务。
3. 监听器的 RuntimeException 属于旁路失败，不改变 Mapper 的返回值、异常或事务结果；组合监听器之间互不阻断。
4. 新配置 `xjtu.iron.relational.direct.enabled=true` 自动创建 Direct 资源目录及 Access/Transaction Resolver，不需要引入幂等组件。旧幂等 Direct 配置继续生效，通过导入公共资源配置创建目录，保留幂等的路由校验与事务协调。
5. 每个 Direct 资源仍从同一 DataSource 创建 MyBatis 与事务运行时。提供按 dataSourceKey 定制 SqlSessionFactoryBean 的扩展点，以及 Listener 注入；定制后的运行时仍必须通过资源校验。连接池由应用拥有，资源目录不创建或关闭连接池。
6. Cache `evict` 先删除 Provider 缓存，成功后发布一次携带 Trace 的失效事件。删除失败不发布事件；发布失败按 IGNORE/THROW 执行。事件发布失败不伪装成缓存删除失败。

## 验收场景

- DataSource/Template 错配、非 Spring 事务工厂在构造阶段拒绝；支持 TransactionAware 代理与目标资源匹配。
- 无事务执行、同源提交/回滚、异源外层事务及自动提交的 synchronization-only 连接拒绝；可靠任务与业务数据保持同源原子性。
- before/success/failure 监听器故障不改变数据库结果或遮蔽原始异常，后续监听器继续执行。
- 只依赖 Relational Starter 的多库装配、用户目录覆盖、每库定制和观测、既有幂等 Direct 装配均通过测试。
- Cache 删除一次只发布一次事件，包含 Trace；IGNORE 不抛异常，THROW 保留发布异常原因；删除失败不发布。

## 兼容性与后续工作

错误资源配对及实际事务下的隐式异源访问会更早失败。需要独立提交的调用方应显式选择独立事务，不能依赖普通 execute 静默跨库。

保留既有构造函数和幂等 Direct 配置。公共 Direct 开关只提供资源目录与解析器，不自动赋予各组件多库路由能力，也不自动复制应用的全部 MyBatis 配置。

消息消费真实适配器、事务内终态协作、跨层重试预算及完整中间件联调属于后续独立变更。本文中的验收场景需由实际执行结果证明，不能作为已通过验证的报告。

## 本轮验证记录（2026-10-16）

验证基线为 `feature/mybatis-persistence-unification-v1` 的 `60158b1a` 加本轮修改。实际执行：

```bash
python3 scripts/validate-poms.py
python3 scripts/check-source-dependencies.py
mvn -o -B -ntp -pl :relational-mybatis,:relational-starter,:cache-core,:reliable-task-provider-mybatis,:idempotent-storage-routing-e2e -am test
mvn -o -B -ntp -pl :relational-starter,:idempotent-storage-routing-e2e,:reliable-task-spring-boot-starter,:distributed-lock-starter,:cache-starter -am test
mvn -o -B -ntp -pl :idempotent-storage-routing-e2e -am test
git diff --check
```

- POM 校验：0 错误，9 个既有 Demo 可执行打包插件警告。
- 源码直接依赖校验：0 缺失。
- 第一批定向测试：37 个模块通过，154 项测试通过。
- 扩展 Reactor：55 个模块通过，227 项中 225 通过、2 项因无 Docker 跳过；跳过项为真实 Redis 锁集成测试。
- 最后追加公共 Direct 与旧幂等开关同时启用的回归测试，相关 Reactor 再次通过，Direct 装配类 7 项测试全部通过。
- 公共资源配置仅支持显式导入，避免组件扫描绕过 Direct 开关；补充该回归后，相关 Reactor 再次通过，Relational Direct 自动配置类 5 项测试全部通过。
- 本轮新增 21 项回归测试，覆盖资源错配、事务回滚/错源拒绝、同步绑定但仍自动提交、Listener 隔离、每库工厂定制、Direct 公共及兼容装配、组件扫描隔离、缓存事件与失败策略。
- 修改文档中的相对路径已检查；失效的 sandbox 引用已改为仓库相对链接。

本轮没有执行全仓 `clean verify`、真实 MySQL 写入验收、Direct MySQL IT 或 MQ/ShardingSphere 联调。
