package com.xjtu.iron.idempotent.api.execution;

import com.xjtu.iron.idempotent.api.policy.IdempotencyMode;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;

import java.time.Instant;
import java.util.Objects;

/**
 * 业务 callback 获取到的当前幂等执行权上下文。
 *
 * <p>只有真正抢到 PROCESSING 执行权的调用才会拿到该对象。V2 同时暴露 StorageContext，
 * 使业务/适配层在需要记录诊断信息或向后续任务传递恢复信息时，可以拿到稳定的 storeName/scanBucket。</p>
 */
public final class IdempotencyContext {

    /** 当前幂等记录的逻辑存储上下文，业务异步恢复链路应原样传递。 */
    private final IdempotencyStorageContext storageContext;

    /** Policy 解析出的业务隔离域。 */
    private final String namespace;

    /** 当前逻辑幂等 Key。 */
    private final String key;

    /** 业务路由元数据，来自请求并做空白归一化。 */
    private final String routeKey;

    /** 当前 generation 的 owner；完成 SUCCESS/FAILED 时必须继续使用它。 */
    private final String ownerToken;

    /** 当前 generation 版本；Recovery 接管或 WINDOWED 窗口重启时递增。 */
    private final long version;

    /** 当前策略模式：WINDOWED 或 DURABLE。 */
    private final IdempotencyMode mode;

    /** true 表示该次 callback 来自显式 recover()，而非普通 execute()。 */
    private final boolean recoveryExecution;

    /** 当前 generation 获得执行权的时间。 */
    private final Instant acquiredAt;

    /** 当前 PROCESSING 执行租约过期时间；只表示可恢复判断点，不表示线程一定死亡。 */
    private final Instant processingExpireAt;

    public IdempotencyContext(IdempotencyStorageContext storageContext, String namespace, String key, String routeKey, String ownerToken,
                              long version, IdempotencyMode mode, boolean recoveryExecution, Instant acquiredAt,
                              Instant processingExpireAt) {
        this.storageContext = Objects.requireNonNull(storageContext, "storageContext must not be null");
        this.namespace = namespace;
        this.key = key;
        this.routeKey = routeKey;
        this.ownerToken = ownerToken;
        this.version = version;
        this.mode = mode;
        this.recoveryExecution = recoveryExecution;
        this.acquiredAt = acquiredAt;
        this.processingExpireAt = processingExpireAt;
    }

    public IdempotencyStorageContext getStorageContext() { return storageContext; }
    public String getStoreName() { return storageContext.getStoreName(); }
    public int getScanBucket() { return storageContext.getScanBucket(); }
    public String getNamespace() { return namespace; }
    public String getKey() { return key; }
    public String getRouteKey() { return routeKey; }
    public String getOwnerToken() { return ownerToken; }
    public long getVersion() { return version; }

    /**
     * 当前幂等 generation 的版本号。它用于幂等记录自身的 owner/version CAS，不能默认等同于 distributed-lock fencingToken。
     */
    public long getGenerationVersion() { return version; }

    public IdempotencyMode getMode() { return mode; }
    public boolean isRecoveryExecution() { return recoveryExecution; }
    public Instant getAcquiredAt() { return acquiredAt; }
    public Instant getProcessingExpireAt() { return processingExpireAt; }
}
