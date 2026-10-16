package com.xjtu.iron.idempotent.api.repository;

import com.xjtu.iron.idempotent.api.policy.IdempotencyMode;
import com.xjtu.iron.idempotent.api.repository.acquire.IdempotencyAcquireRequest;
import com.xjtu.iron.idempotent.api.repository.acquire.IdempotencyAcquireResult;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryAcquireRequest;
import com.xjtu.iron.idempotent.api.repository.recovery.IdempotencyRecoveryResult;
import com.xjtu.iron.idempotent.api.repository.write.IdempotencyDiscardRequest;
import com.xjtu.iron.idempotent.api.repository.write.IdempotencyFailureRequest;
import com.xjtu.iron.idempotent.api.repository.write.IdempotencySuccessRequest;
import com.xjtu.iron.idempotent.api.repository.write.IdempotencyWriteResult;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;

import java.util.Optional;

/**
 * 幂等状态正确性的核心 SPI。
 *
 * <p>DistributedLockClient 只能减少热点竞争；即使完全关闭分布式锁，Repository 自己也必须依靠
 * UNIQUE / 行锁 / Lua / CAS 保证原子状态转换。</p>
 *
 * <p>V2 的 generation 生命周期写动作：</p>
 * <pre>
 * tryAcquire    -> 普通请求争夺/判断当前 generation
 * tryRecover    -> Reliable Task 二次 CAS，安全产生下一代 generation
 * markSuccess   -> 当前 owner/version 完成 SUCCESS
 * markFailed    -> 当前 owner/version 完成 FAILED
 * markDiscarded -> 当前 owner/version 完成 DISCARDED
 * </pre>
 */
public interface IdempotencyRepository {

    /** Provider 稳定名称，例如 jdbc / redis。 */
    String providerName();

    /** Provider 能力声明。Core 不通过 providerName 猜测 WINDOWED/DURABLE/事务/Recovery 能力。 */
    IdempotencyRepositoryCapabilities capabilities();

    default boolean supports(IdempotencyMode mode) { return capabilities().supports(mode); }

    default boolean supportsBusinessTransactionParticipation() {
        return capabilities().isBusinessTransactionParticipationSupported();
    }

    /** 普通 execute() 的原子状态入口。只有返回 ACQUIRED，当前调用才真正获得 execution generation。 */
    IdempotencyAcquireResult tryAcquire(IdempotencyAcquireRequest request);

    /** 显式 Recovery 的原子接管入口。expectedOwner + expectedVersion 必须在这里再次验证。 */
    IdempotencyRecoveryResult tryRecover(IdempotencyRecoveryAcquireRequest request);

    /** 当前 generation 完成 SUCCESS。实现必须用 ownerToken + version 条件拒绝 stale owner。 */
    IdempotencyWriteResult markSuccess(IdempotencySuccessRequest request);

    /** 当前 generation 完成 FAILED。失败写同样必须受 ownerToken + version 保护。 */
    IdempotencyWriteResult markFailed(IdempotencyFailureRequest request);

    /** 当前 generation 明确进入 DISCARDED 终态，后续重复请求不得重新执行业务。 */
    IdempotencyWriteResult markDiscarded(IdempotencyDiscardRequest request);

    /**
     * 只读查询，不授予执行权。V2 必须携带 StorageContext，避免不同 storeName 下相同 namespace/key 被错误查询到。
     */
    Optional<IdempotencyRecord> find(IdempotencyStorageContext storageContext, String namespace, String key);
}
