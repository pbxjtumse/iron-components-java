0. fencingToken 解决了什么 ？
A 获取锁后卡住；
锁过期；
B 获取锁并完成业务写入；
A 恢复后继续执行旧业务逻辑；
A 把 B 的新结果覆盖掉。

这个问题靠 Redis 锁本身解决不了。

因为 Redis 锁只能回答：

当前 Redis key 属不属于这个 ownerToken？

但它不能保护：

业务数据库中的那一行数据，是否允许被旧 owner 修改？

1. ownerToken 和 fencingToken 的区别

这是二期最重要的理解点。

1.1 ownerToken 管锁本身

ownerToken 是一次加锁成功后的身份标识。

比如：

ownerToken = 3f4a2c9e-xxxx

它用于 Redis Lua：

release.lua
renew.lua
check.lua

判断：

这个释放/续期/检查请求是不是当前锁持有者发起的？

所以 ownerToken 保护的是：

不要误删别人的 Redis 锁；
不要给别人的 Redis 锁续期；
不要误判自己还持有锁。

它主要作用在锁系统内部。

.2 fencingToken 管业务资源

fencingToken 是一个单调递增数字：

1
2
3
4
...

每次成功获得锁后，业务方拿到一个更大的 token。

业务写数据库时必须带上这个 token：

UPDATE resource
SET payload = ?,
last_fencing_token = ?
WHERE resource_key = ?
AND last_fencing_token < ?;

这表示：

只有新 token 才能写入；
旧 token 不能覆盖新 token。




所以 fencingToken 保护的是：
当前操作是不是最新锁持有者发起的
旧持有者是否应被拒绝

业务数据库、库存、订单状态、任务状态这些真正的业务资源。

它主要作用在业务系统外部资源。


1.3 一句话区分
ownerToken：
防止误操作锁。

fencingToken：
防止旧 owner 覆盖新 owner 的业务结果。


4.4 RedisLockProvider 怎么接收 token？

RedisLockProvider.acquire() 中做了三件事：

List<String> args = Arrays.asList(
request.getOwnerToken(),
String.valueOf(request.getOptions().getLeaseTime().toMillis()),
request.isNativeFencingRequired() ? "1" : "0"
);

这里的关键是：

request.isNativeFencingRequired()

它不是简单等于：

options.isFencingRequired()

因为二期有两种 fencing：

Redis native fencing
JDBC external fencing

如果使用 JDBC sequence，就不能让 Redis 也 INCR 一次。
否则会变成“双重发号”。

所以当前逻辑是：

NATIVE 模式：
nativeFencingRequired = true

EXTERNAL 模式：
nativeFencingRequired = false

Redis 返回 token 后，会塞进 LockLease：

LockLease lease = LockLease.builder()
.providerName(providerName())
.namespace(request.getNamespace())
.lockName(request.getLockName())
.lockKey(lockKey)
.ownerToken(request.getOwnerToken())
.fencingToken(result.getFencingToken())
.fencingTokenProviderName(result.getFencingToken() == null ? null : providerName())
.leaseTime(request.getOptions().getLeaseTime())
.build();

所以 Redis 原生 fencing 的 token 来源是：

fencingTokenProviderName = redis


5. JDBC sequence fencing：EXTERNAL 模式
   5.1 为什么还要 JDBC？

Redis fencing 虽然简单，但对于真正强一致业务，我们更希望 token 来自业务数据库。

原因是：

业务资源在数据库；
版本号也在数据库；
条件写入也在数据库；
这样一致性边界更清楚。

比如订单、任务状态、库存、资金类数据，最好使用：

DB sequence / DB 表版本号

生成 fencing token。


5.2 JDBC provider 在哪里？

模块：

distributed-lock-provider
└── distributed-lock-fencing-provider-mybatis

包：

com.xjtu.iron.distributed.lock.provider.mybatis.fencing

核心类：

MyBatisSequenceFencingTokenProvider
MyBatis schema scripts
MyBatisFencingTokenConstants

注意，它不是 LockProvider。

它不负责加锁、解锁、续期。

它只负责：

生成 fencing token。

所以它实现的是：

public interface FencingTokenProvider {
String providerName();
boolean supports(FencingTokenRequest request);
FencingTokenResponse nextToken(FencingTokenRequest request);
}


5.3 JDBC provider 的表结构

当前设计的发号表是：

CREATE TABLE IF NOT EXISTS iron_lock_fencing_token (
namespace VARCHAR(128) NOT NULL,
lock_name VARCHAR(512) NOT NULL,
current_token BIGINT NOT NULL,
updated_at TIMESTAMP NOT NULL
DEFAULT CURRENT_TIMESTAMP
ON UPDATE CURRENT_TIMESTAMP,
PRIMARY KEY (namespace, lock_name)
);

主键是：

namespace + lock_name

所以每一把锁都有自己独立递增序列。

例如：

demo:order:1 -> 1,2,3,4
demo:order:2 -> 1,2,3,4

不同锁互不影响。
