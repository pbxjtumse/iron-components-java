# Relational Access V1 文档

当前版本已收口为 MyBatis-first 技术表接入层。详细边界、调用链、Direct 多库资源目录和接入约束见 [组件 README](../README.md)。

旧 `RelationalTemplate` 设计文档已随原生 JDBC 执行链一同删除，避免将已下线 API 当成现行用法。

各组件仍然保留自己的业务持久化代码：

- Repository SPI
- MyBatis Mapper
- Mapper XML
- 表结构脚本
- 状态机和 CAS 语义

但不再分别处理这些通用问题：

- 获取和管理 JDBC Connection
- PreparedStatement 参数绑定
- ResultSet 映射基础设施
- Spring 事务连接复用
- REQUIRED、REQUIRES_NEW 等事务执行方式
- 多数据源选择
- Storage Routing 物理数据源解析
- SQL 异常转换
- 重复键异常识别
- 访问监听与可观测性

Repository 的稳定扩展关系如下：

```text
reliable-task-core
  -> ReliableTaskRepository
       -> MyBatisReliableTaskRepository (V1)
       -> JdbcReliableTaskRepository    (未来可重新实现)
       -> JpaReliableTaskRepository     (未来可实现)
```

因此 V1 统一 MyBatis 并不等于永久锁死 MyBatis。Core 依赖 Repository SPI；未来增加
JDBC 或 JPA Provider 时，应新增 Provider 实现，而不是改变 Core 或复活一套与 MyBatis
并行的公共 SQL 模板。
