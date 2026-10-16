package com.xjtu.iron.idempotent.api.repository.write;

import com.xjtu.iron.idempotent.api.policy.IdempotencyMode;
import com.xjtu.iron.idempotent.api.policy.IdempotencyWindowPolicy;
import com.xjtu.iron.idempotent.api.storage.IdempotencyStorageContext;

import java.time.Duration;
import java.time.Instant;

/**
 * PROCESSING -> DISCARDED 条件写请求。
 *
 * <p>DISCARDED 是明确终态，适合“业务确认无需执行但也不应被重试”的场景。
 * 它和 SUCCESS 一样必须通过 ownerToken + version 条件写入。</p>
 */
public final class IdempotencyDiscardRequest {

    /** 逻辑存储上下文。 */
    private final IdempotencyStorageContext storageContext;

    /** 幂等隔离域。 */
    private final String namespace;

    /** 逻辑幂等 Key。 */
    private final String key;

    /** 当前 generation owner。 */
    private final String ownerToken;

    /** 当前 generation version。 */
    private final long version;

    /** 可选结果载荷；重复命中 DISCARDED 时不会走 ResultPolicy replay。 */
    private final String resultPayload;

    /** 幂等模式。 */
    private final IdempotencyMode mode;

    /** WINDOWED 语义窗口。 */
    private final Duration idempotencyWindow;

    /** WINDOWED 窗口推进策略。 */
    private final IdempotencyWindowPolicy windowPolicy;

    /** 语义窗口结束后的额外物理保留时间。 */
    private final Duration recordRetentionTtl;

    /** Core 传入的统一当前时间。 */
    private final Instant now;

    public IdempotencyDiscardRequest(IdempotencyStorageContext storageContext, String namespace, String key, String ownerToken, long version,
                                     String resultPayload, IdempotencyMode mode, Duration idempotencyWindow,
                                     IdempotencyWindowPolicy windowPolicy, Duration recordRetentionTtl, Instant now) {
        this.storageContext = storageContext;
        this.namespace = namespace;
        this.key = key;
        this.ownerToken = ownerToken;
        this.version = version;
        this.resultPayload = resultPayload;
        this.mode = mode;
        this.idempotencyWindow = idempotencyWindow;
        this.windowPolicy = windowPolicy;
        this.recordRetentionTtl = recordRetentionTtl;
        this.now = now;
    }

    public IdempotencyStorageContext getStorageContext() { return storageContext; }
    public String getNamespace() { return namespace; }
    public String getKey() { return key; }
    public String getOwnerToken() { return ownerToken; }
    public long getVersion() { return version; }
    public String getResultPayload() { return resultPayload; }
    public IdempotencyMode getMode() { return mode; }
    public Duration getIdempotencyWindow() { return idempotencyWindow; }
    public IdempotencyWindowPolicy getWindowPolicy() { return windowPolicy; }
    public Duration getRecordRetentionTtl() { return recordRetentionTtl; }
    public Instant getNow() { return now; }
}
