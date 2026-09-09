# Real MySQL Integration Test

> 目标：在真实 MySQL 上验证 Relational Access 的多库路由与同库同事务行为。

## 1. 为什么还要真实 MySQL 测试

H2 测试用于保证基础流程稳定、快速、可在普通开发环境执行。

真实 MySQL 测试用于验证更接近生产的行为：

```text
InnoDB 本地事务
真实 JDBC Driver
真实 DataSourceUtils 连接绑定
真实 SQLState / vendorCode
真实网络连接与数据库会话
```

因此二者不是替代关系：

```text
H2      -> 默认跑，保证组件基础正确
MySQL   -> 配置环境变量后跑，验证真实数据库行为
```

## 2. 测试类

```text
SameShardMysqlSharedTransactionIntegrationTest
```

路径：

```text
relational-integration/relational-integration-spring/src/test/java/com/xjtu/iron/relational/integration/spring/SameShardMysqlSharedTransactionIntegrationTest.java
```

该测试使用两个真实 MySQL schema 模拟两个分片库：

```text
order-db-0 -> IRON_TEST_MYSQL_ORDER0_URL
order-db-1 -> IRON_TEST_MYSQL_ORDER1_URL
```

业务数据用 `JdbcTemplate` 模拟 MyBatis，幂等记录用 `RelationalTemplate` 模拟 `JdbcIdempotencyStorage`。

## 3. 环境变量

代码只读取环境变量，不在仓库提交真实账号密码。

```bash
export IRON_TEST_MYSQL_ORDER0_URL='jdbc:mysql://<host>:<port>/iron_order_0?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8'
export IRON_TEST_MYSQL_ORDER1_URL='jdbc:mysql://<host>:<port>/iron_order_1?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8'
export IRON_TEST_MYSQL_USERNAME='iron_test'
export IRON_TEST_MYSQL_PASSWORD='your-password'
```

如果这些变量没有配置，测试会自动 skip，不影响普通构建。

## 4. 测试会创建的表

为了避免污染业务表，测试使用专用表名：

```text
iron_it_business_order
iron_it_idempotency_record
```

建表语句由测试自动执行：

```sql
CREATE TABLE IF NOT EXISTS iron_it_business_order (
    order_id VARCHAR(64) PRIMARY KEY,
    amount DECIMAL(18, 2) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS iron_it_idempotency_record (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    biz_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

测试数据统一使用 `mysql-it-` 前缀，执行前后会清理该前缀的数据。

## 5. 验证内容

### 同 shard 提交

```text
TransactionManager(order-db-1)
  -> JdbcTemplate insert iron_it_business_order
  -> RelationalTemplate route order-db-1 insert iron_it_idempotency_record
  -> commit
```

结果：

```text
order-db-1.business_order = 1
order-db-1.idempotency_record = 1
order-db-0 无数据
```

### 同 shard 回滚

```text
TransactionManager(order-db-1)
  -> JdbcTemplate insert business
  -> RelationalTemplate route order-db-1 insert idempotency
  -> throw exception
  -> rollback
```

结果：

```text
order-db-1 business + idempotency 全部回滚
order-db-0 无数据
```

### 错 shard 风险验证

```text
TransactionManager(order-db-1)
  -> JdbcTemplate insert business 到 order-db-1
  -> RelationalTemplate 错 route 到 order-db-0
  -> throw exception
  -> rollback order-db-1
```

结果：

```text
order-db-1 business 回滚
order-db-1 idempotency 无数据
order-db-0 idempotency 仍存在
```

这个测试证明：

```text
幂等记录和业务数据必须落到同一个 shard。
否则普通本地事务无法保证一起回滚。
```

## 6. 执行命令

```bash
mvn -pl :relational-integration-spring -am test
```

配置了环境变量会跑真实 MySQL 测试；未配置则跳过真实 MySQL 测试，只跑普通 H2 测试。

## 7. 后续演进

真实 MySQL 测试打穿后，再推进：

```text
1. 抽 sharding-component 的 StorageRoute / StorageRouteResolver
2. Idempotency JDBC Storage 接 StorageRoute
3. ShardingSphere-JDBC 模式验证 MyBatis + RelationalTemplate 共用 ShardingSphereDataSource
```
