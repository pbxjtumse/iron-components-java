package com.xjtu.iron.distributed.lock.api.model;

import com.xjtu.iron.distributed.lock.api.LockStatusStageRules;
import com.xjtu.iron.distributed.lock.api.client.DistributedLockClient;
import com.xjtu.iron.distributed.lock.api.client.LockCallback;
import com.xjtu.iron.distributed.lock.api.status.LockStage;
import com.xjtu.iron.distributed.lock.api.status.LockStatus;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * 分布式锁操作最终结果
 *
 * <p>LockResult 是 {@link DistributedLockClient#tryLock(String, LockOptions)} 和
 * {@link DistributedLockClient#execute(String, LockOptions, LockCallback)} 返回给业务方的统一结果对象。</p>
 *
 * <p>它同时表达三层含义：</p>
 * <ul>
 *     <li>{@code status}：最终结果是什么，例如 SUCCESS、NOT_ACQUIRED、LOCK_LOST。</li>
 *     <li>{@code stage}：该最终结果发生在哪个阶段，例如 ACQUIRE、RENEW、RELEASE。</li>
 *     <li>{@code acquired}：本次操作是否曾经成功获取过锁。</li>
 * </ul>
 *
 * <p>
 * 注意：status 和 acquired 不能混为一谈。
 * 例如 execute 业务执行失败时，status = EXECUTION_FAILED，但 acquired = true，
 * 因为它确实曾经拿到过锁。
 * </p>
 *
 * @param <T> 结果值类型
 */
public final class LockResult<T> {

    /**
     * 操作最终状态。
     *
     * <p>
     * 表达这次 tryLock / execute 最终是什么结果。
     * </p>
     */
    private final LockStatus status;

    /**
     * 决定最终结果的生命周期阶段。
     *
     * <p>
     * 例如：
     * </p>
     *
     * <ul>
     *     <li>加锁时 Redis 异常：status = PROVIDER_ERROR，stage = ACQUIRE。</li>
     *     <li>续期时发现 owner 不匹配：status = LOCK_LOST，stage = RENEW。</li>
     *     <li>释放时 Redis 异常：status = RELEASE_FAILED，stage = RELEASE。</li>
     * </ul>
     */
    private final LockStage stage;

    /**
     * 本次操作是否曾经成功获取过锁。
     *
     * <p>
     * acquired = true 不代表最终成功，只代表曾经拿到过锁。
     * 例如业务执行失败、释放失败、fencing 被拒绝时，acquired 仍然可能是 true。
     * </p>
     */
    private final boolean acquired;

    /**
     * execute 成功时的业务返回值。
     *
     * <p>
     * tryLock 场景通常为空。
     * execute 业务返回 null 时也为空。
     * </p>
     */
    private final T value;

    /**
     * tryLock 成功时返回的锁句柄。
     *
     * <p>
     * execute 场景下通常也可以保留 handle，方便排查 ownerToken / fencingToken。
     * 不过业务正常情况下应通过 callback 入参使用 handle。
     * </p>
     */
    private final LockHandle handle;

    /**
     * 失败异常。
     *
     * <p>
     * NOT_ACQUIRED 这类正常竞争失败可以没有 error。
     * PROVIDER_ERROR、EXECUTION_FAILED、RELEASE_FAILED 等通常应包含 error。
     * </p>
     */
    private final Throwable error;

    /**
     * 业务锁名称。
     */
    private final String lockName;

    /**
     * 底层 Provider 实际使用的锁 key / path。
     *
     * <p>
     * Redis Provider 中一般是 Redis key。
     * ZK Provider 中可能是 ZK path。
     * Etcd Provider 中可能是 etcd key。
     * </p>
     */
    private final String lockKey;

    /**
     * 本次锁租约的 owner token。
     *
     * <p>
     * ownerToken 用于证明锁归属，释放和续期时必须校验它。
     * </p>
     */
    private final String ownerToken;

    /**
     * fencing token。
     *
     * <p>
     * fencingToken 是业务资源写入版本号，用于防止旧 owner 恢复后覆盖新 owner 的结果。
     * 没有启用 fencing 时为空。
     * </p>
     */
    private final Long fencingToken;

    /** fencing token 来源 Provider。 */
    private final String fencingTokenProviderName;

    /**
     * 获取锁等待耗时。
     */
    private final Duration waitDuration;

    /**
     * 持锁耗时。
     *
     * <p>
     * 从加锁成功到释放完成、业务失败、锁丢失之间的耗时。
     * </p>
     */
    private final Duration holdDuration;

    private LockResult(Builder<T> builder) {
        this.status = Objects.requireNonNull(builder.status, "status must not be null");
        this.stage = Objects.requireNonNull(builder.stage, "stage must not be null");
        this.acquired = builder.acquired;
        LockStatusStageRules.validate(this.status, this.stage, this.acquired);
        this.value = builder.value;
        this.handle = builder.handle;
        this.error = builder.error;
        this.lockName = builder.lockName;
        this.lockKey = builder.lockKey;
        this.ownerToken = builder.ownerToken;
        this.fencingToken = builder.fencingToken;
        this.fencingTokenProviderName = builder.fencingTokenProviderName;
        this.waitDuration = builder.waitDuration;
        this.holdDuration = builder.holdDuration;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * 创建 tryLock 成功结果。
     */
    public static LockResult<LockHandle> acquired(LockHandle handle, Duration waitDuration) {
        Objects.requireNonNull(handle, "handle must not be null");

        return LockResult.<LockHandle>builder()
                .status(LockStatus.ACQUIRED)
                .stage(LockStage.ACQUIRE)
                .acquired(true)
                .value(handle)
                .handle(handle)
                .lockName(handle.lockName())
                .lockKey(handle.lockKey())
                .ownerToken(handle.ownerToken())
                .fencingToken(handle.fencingToken().isPresent() ? handle.fencingToken().getAsLong() : null)
                .fencingTokenProviderName(handle.fencingTokenProviderName().orElse(null))
                .waitDuration(waitDuration)
                .build();
    }

    /**
     * 创建 execute 成功结果。
     */
    public static <T> LockResult<T> success(T value, LockHandle handle, Duration waitDuration, Duration holdDuration) {
        Builder<T> builder = LockResult.<T>builder()
                .status(LockStatus.SUCCESS)
                .stage(LockStage.EXECUTE)
                .acquired(true)
                .value(value)
                .handle(handle)
                .waitDuration(waitDuration)
                .holdDuration(holdDuration);
        fillHandleFields(builder, handle);
        return builder.build();
    }

    /**
     * 创建未获取到锁结果。
     *
     * <p>stage 只能是 ACQUIRE 或 WAIT。waitTime=0 首次抢锁失败时使用 ACQUIRE；
     * waitTime&gt;0 等待超时后仍未获取到锁时使用 WAIT。</p>
     */
    public static <T> LockResult<T> notAcquired(String lockName, String lockKey, LockStage stage, Duration waitDuration) {
        if (stage != LockStage.ACQUIRE && stage != LockStage.WAIT) {
            throw new IllegalArgumentException("NOT_ACQUIRED stage must be ACQUIRE or WAIT");
        }
        return LockResult.<T>builder()
                .status(LockStatus.NOT_ACQUIRED)
                .stage(stage)
                .acquired(false)
                .lockName(lockName)
                .lockKey(lockKey)
                .waitDuration(waitDuration)
                .build();
    }

    /**
     * 创建失败结果。
     */
    public static <T> LockResult<T> failure(LockStatus status, LockStage stage, boolean acquired, Throwable error) {
        return LockResult.<T>builder()
                .status(status)
                .stage(stage)
                .acquired(acquired)
                .error(error)
                .build();
    }

    private static <T> void fillHandleFields(Builder<T> builder, LockHandle handle) {
        if (handle == null) {
            return;
        }
        builder.lockName(handle.lockName())
                .lockKey(handle.lockKey())
                .ownerToken(handle.ownerToken())
                .fencingToken(handle.fencingToken().isPresent() ? handle.fencingToken().getAsLong() : null)
                .fencingTokenProviderName(handle.fencingTokenProviderName().orElse(null));
    }

    public LockStatus status() {
        return status;
    }

    public LockStatus getStatus() {
        return status;
    }

    public LockStage stage() {
        return stage;
    }

    public LockStage getStage() {
        return stage;
    }

    public boolean acquired() {
        return acquired;
    }

    public boolean isAcquired() {
        return acquired;
    }

    public boolean success() {
        return status == LockStatus.SUCCESS || status == LockStatus.ACQUIRED;
    }

    public boolean isSuccess() {
        return success();
    }

    public boolean notAcquired() {
        return status == LockStatus.NOT_ACQUIRED;
    }

    public boolean isNotAcquired() {
        return notAcquired();
    }

    public Optional<T> value() {
        return Optional.ofNullable(value);
    }

    public Optional<LockHandle> handle() {
        return Optional.ofNullable(handle);
    }

    public Optional<Throwable> error() {
        return Optional.ofNullable(error);
    }

    public String lockName() {
        return lockName;
    }

    public String lockKey() {
        return lockKey;
    }

    public String ownerToken() {
        return ownerToken;
    }

    public Optional<Long> fencingToken() {
        return Optional.ofNullable(fencingToken);
    }

    public Optional<String> fencingTokenProviderName() {
        return Optional.ofNullable(fencingTokenProviderName);
    }

    public Duration waitDuration() {
        return waitDuration;
    }

    public Duration holdDuration() {
        return holdDuration;
    }

    /** LockResult 构造器。 */
    public static final class Builder<T> {

        private LockStatus status;
        private LockStage stage;
        private boolean acquired;
        private T value;
        private LockHandle handle;
        private Throwable error;
        private String lockName;
        private String lockKey;
        private String ownerToken;
        private Long fencingToken;
        private String fencingTokenProviderName;
        private Duration waitDuration;
        private Duration holdDuration;

        private Builder() {
        }

        public Builder<T> status(LockStatus status) {
            this.status = status;
            return this;
        }

        public Builder<T> stage(LockStage stage) {
            this.stage = stage;
            return this;
        }

        public Builder<T> acquired(boolean acquired) {
            this.acquired = acquired;
            return this;
        }

        public Builder<T> value(T value) {
            this.value = value;
            return this;
        }

        public Builder<T> handle(LockHandle handle) {
            this.handle = handle;
            return this;
        }

        public Builder<T> error(Throwable error) {
            this.error = error;
            return this;
        }

        public Builder<T> lockName(String lockName) {
            this.lockName = lockName;
            return this;
        }

        public Builder<T> lockKey(String lockKey) {
            this.lockKey = lockKey;
            return this;
        }

        public Builder<T> ownerToken(String ownerToken) {
            this.ownerToken = ownerToken;
            return this;
        }

        public Builder<T> fencingToken(Long fencingToken) {
            this.fencingToken = fencingToken;
            return this;
        }

        public Builder<T> fencingTokenProviderName(String fencingTokenProviderName) {
            this.fencingTokenProviderName = fencingTokenProviderName;
            return this;
        }

        public Builder<T> waitDuration(Duration waitDuration) {
            this.waitDuration = waitDuration;
            return this;
        }

        public Builder<T> holdDuration(Duration holdDuration) {
            this.holdDuration = holdDuration;
            return this;
        }

        public LockResult<T> build() {
            return new LockResult<>(this);
        }
    }
}
