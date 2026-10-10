package com.xjtu.iron.distributed.lock.api.model;

import com.xjtu.iron.distributed.lock.api.LockWaitStrategy;
import com.xjtu.iron.distributed.lock.api.RetryBackoffSpec;
import com.xjtu.iron.distributed.lock.api.exception.InvalidLockOptionsException;

import java.time.Duration;

/**
 * 分布式锁选项。
 *
 * <p>
 * LockOptions 描述一次加锁请求的行为，包括：
 * 等待多久、锁租约多久、是否自动续期、是否要求 fencing token、
 * 失锁后是否失败、使用哪个底层 Provider 等。
 * </p>
 *
 * <p>
 * 注意：
 * LockOptions 是一次加锁请求的配置快照，不表示锁本身。
 * 锁真正获取成功之后，会返回 LockHandle 和 LockLease。
 * </p>
 */
public final class LockOptions {

    /**
     * 默认命名空间。
     *
     * <p>
     * namespace 用于隔离不同业务系统、不同模块的锁 key。
     * </p>
     */
    public static final String DEFAULT_NAMESPACE = "default";

    /**
     * 默认等待时间。
     *
     * <p>
     * Duration.ZERO 表示不等待，抢不到锁立即返回 NOT_ACQUIRED。
     * </p>
     */
    public static final Duration DEFAULT_WAIT_TIME = Duration.ZERO;

    /**
     * 默认锁租约时间。
     *
     * <p>
     * Redis Provider 下表示 lock key 的初始 TTL。
     * </p>
     */
    public static final Duration DEFAULT_LEASE_TIME = Duration.ofSeconds(30);

    /**
     * 默认最大自动续期时间。
     *
     * <p>
     * 只有 autoRenew=true 时才有实际意义。
     * 用于防止 watchdog 无限续期。
     * </p>
     */
    public static final Duration DEFAULT_MAX_RENEW_TIME = Duration.ofMinutes(10);

    /**
     * 默认是否自动续期。
     *
     * <p>
     * 一期默认关闭自动续期。
     * </p>
     */
    public static final boolean DEFAULT_AUTO_RENEW = false;

    /**
     * 默认是否要求 fencing token。
     *
     * <p>
     * 默认不要求 fencing token。
     * 二期已支持 Redis 原生 INCR 与独立 JDBC sequence Provider。
     * </p>
     */
    public static final boolean DEFAULT_FENCING_REQUIRED = false;

    /**
     * 默认失锁后是否认为本次执行失败。
     *
     * <p>
     * true 表示一旦组件明确发现当前 LockHandle 已经失锁，
     * execute 最终应该返回 LOCK_LOST。
     * </p>
     */
    public static final boolean DEFAULT_FAIL_ON_LOCK_LOST = true;

    /**
     * 命名空间。
     *
     * <p>
     * 用于构造底层锁 key，避免不同业务之间 lockName 冲突。
     * </p>
     */
    private final String namespace;

    /**
     * 最长等待时间。
     *
     * <p>
     * waitTime = 0 表示不等待；
     * waitTime > 0 表示在指定时间内按照等待策略重试获取锁。
     * </p>
     */
    private final Duration waitTime;

    /**
     * 锁租约时间。
     *
     * <p>
     * CORE_MANAGED Redis Provider 下，每次加锁或续期都会把 TTL 设置为 leaseTime；
     * PROVIDER_MANAGED Redisson 下，autoRenew=true 时 leaseTime 表示与 client watchdogTimeout 对齐的 TTL 时间窗。
     * </p>
     */
    private final Duration leaseTime;

    /**
     * 等待策略。
     *
     * <p>
     * 当前支持三种职责边界：
     * NO_WAIT：不等待；
     * BACKOFF：由 Core 退避 + jitter 重试；
     * PROVIDER_NATIVE：由 Provider 自己完成等待/唤醒，例如 Redisson Pub/Sub、未来 ZK/Etcd watch。
     * </p>
     */
    private final LockWaitStrategy waitStrategy;

    /**
     * 是否开启自动续期。
     *
     * <p>
     * autoRenew=true 时会启用统一 watchdog 生命周期。
     * CORE_MANAGED Provider（自研 Redis Lua）由 Core 定时调用 renew；
     * PROVIDER_MANAGED Provider（Redisson）由 Provider 自己续期，Core 只监视 owner 与 maxRenewTime。
     * </p>
     */
    private final boolean autoRenew;

    /**
     * 自动续期间隔。
     *
     * <p>
     * 默认取 leaseTime / 3。
     * 例如 leaseTime=30s，则 renewInterval 默认 10s。
     * </p>
     */
    private final Duration renewInterval;

    /**
     * 最大自动续期时间。
     *
     * <p>
     * 从加锁成功开始计算。
     * 超过该时间后，watchdog 不再继续续期，防止无限续期。
     * </p>
     */
    private final Duration maxRenewTime;

    /**
     * 是否要求返回 fencing token。
     *
     * <p>
     * 如果为 true，但底层 Provider 或独立 FencingTokenProvider 不支持，
     * 应该在加锁前 fail fast。
     * </p>
     */
    private final boolean fencingRequired;

    /**
     * 失锁后是否让 execute 返回 LOCK_LOST。
     *
     * <p>
     * 如果为 false，某些释放阶段发现的失锁可以仅记录事件，
     * 最终结果仍保留原始业务结果。
     * </p>
     */
    private final boolean failOnLockLost;

    /**
     * 指定底层 Provider 名称。
     *
     * <p>为空表示使用默认 Provider，例如 redis。</p>
     *
     * <p><strong>重要：</strong>Provider 名称不只是实现选择，它同时决定协调域。
     * 当前自研 redis Provider 与 redisson Provider 使用不同物理锁对象/Key 结构，
     * 同一个业务 lockName 在两个 Provider 之间不会天然互斥。因此生产切换 Provider 时必须
     * 先排空旧 Provider 的执行/租约，或通过业务 fencing 等迁移方案保护，不能无保护滚动混跑。</p>
     */
    private final String providerName;

    /**
     * 指定 fencing token Provider 名称。
     *
     * <p>
     * 为空时优先使用锁 Provider 的原生 fencing；
     * 指定当前锁 Provider 名称（例如 redis）时强制使用原生 fencing；
     * 指定独立 Provider 名称（例如 mybatis-sequence）时使用外部发号。
     * </p>
     */
    private final String fencingTokenProviderName;

    /**
     * BACKOFF 等待策略配置。
     *
     * <p>
     * 只有 waitStrategy=BACKOFF 时才有实际意义。
     * </p>
     */
    private final RetryBackoffSpec backoffSpec;

    private LockOptions(Builder builder) {
        this.namespace = builder.namespace;
        this.waitTime = builder.waitTime;
        this.leaseTime = builder.leaseTime;
        this.waitStrategy = builder.waitStrategy;
        this.autoRenew = builder.autoRenew;
        this.renewInterval = builder.renewInterval;
        this.maxRenewTime = builder.maxRenewTime;
        this.fencingRequired = builder.fencingRequired;
        this.failOnLockLost = builder.failOnLockLost;
        this.providerName = builder.providerName;
        this.fencingTokenProviderName = builder.fencingTokenProviderName;
        this.backoffSpec = builder.backoffSpec;
    }

    /**
     * 返回默认锁选项。
     *
     * <p>
     * 默认语义：
     * </p>
     *
     * <ul>
     *     <li>namespace = default；</li>
     *     <li>waitTime = 0，不等待；</li>
     *     <li>waitStrategy = NO_WAIT；</li>
     *     <li>leaseTime = 30 秒；</li>
     *     <li>autoRenew = false，不自动续期；</li>
     *     <li>renewInterval = leaseTime / 3，即默认 10 秒；</li>
     *     <li>maxRenewTime = 10 分钟；</li>
     *     <li>fencingRequired = false；</li>
     *     <li>failOnLockLost = true；</li>
     *     <li>providerName = null，使用默认 Provider；</li>
     *     <li>fencingTokenProviderName = null。</li>
     * </ul>
     *
     * @return 默认锁选项
     */
    public static LockOptions defaults() {
        return builder().build();
    }

    /**
     * 创建不等待的锁选项。
     *
     * <p>
     * 只尝试一次获取锁。
     * 获取失败立即返回 NOT_ACQUIRED。
     * leaseTime 使用默认 30 秒。
     * </p>
     *
     * @return 不等待锁选项
     */
    public static LockOptions noWait() {
        return builder()
                .waitTime(Duration.ZERO)
                .waitStrategy(LockWaitStrategy.NO_WAIT)
                .build();
    }

    /**
     * 创建不等待的锁选项，并指定租约时间。
     *
     * <p>
     * 只尝试一次获取锁。
     * 获取成功后锁租约为 leaseTime。
     * </p>
     *
     * @param leaseTime 锁租约时间
     * @return 不等待锁选项
     */
    public static LockOptions noWait(Duration leaseTime) {
        return builder()
                .waitTime(Duration.ZERO)
                .waitStrategy(LockWaitStrategy.NO_WAIT)
                .leaseTime(leaseTime)
                .build();
    }

    /**
     * 创建带等待时间的锁选项。
     *
     * <p>
     * 在 waitTime 内使用 BACKOFF 策略重试获取锁。
     * leaseTime 使用默认 30 秒。
     * </p>
     *
     * @param waitTime 最长等待时间
     * @return 带等待时间的锁选项
     */
    public static LockOptions waitFor(Duration waitTime) {
        return builder()
                .waitTime(waitTime)
                .waitStrategy(LockWaitStrategy.BACKOFF)
                .build();
    }

    /**
     * 创建带等待时间和租约时间的锁选项。
     *
     * <p>
     * 在 waitTime 内使用 BACKOFF 策略重试获取锁。
     * 获取成功后锁租约为 leaseTime。
     * </p>
     *
     * @param waitTime 最长等待时间
     * @param leaseTime 锁租约时间
     * @return 带等待时间和租约时间的锁选项
     */
    public static LockOptions waitFor(Duration waitTime, Duration leaseTime) {
        return builder()
                .waitTime(waitTime)
                .waitStrategy(LockWaitStrategy.BACKOFF)
                .leaseTime(leaseTime)
                .build();
    }

    /**
     * 创建自动续期的锁选项。
     *
     * <p>
     * 不等待获取锁。
     * leaseTime 使用默认 30 秒。
     * 获取锁成功后启动 watchdog。
     * watchdog 最多续期到 maxRenewTime。
     * </p>
     *
     * @param maxRenewTime 最大自动续期时间
     * @return 自动续期锁选项
     */
    public static LockOptions autoRenew(Duration maxRenewTime) {
        return builder()
                .waitTime(Duration.ZERO)
                .waitStrategy(LockWaitStrategy.NO_WAIT)
                .leaseTime(DEFAULT_LEASE_TIME)
                .autoRenew(maxRenewTime)
                .build();
    }

    /**
     * 创建指定租约时间和最大续期时间的自动续期锁选项。
     *
     * <p>
     * 不等待获取锁。
     * 获取成功后启动 watchdog。
     * 每次续期会把底层 TTL 重置为 leaseTime。
     * </p>
     *
     * @param leaseTime 锁租约时间
     * @param maxRenewTime 最大自动续期时间
     * @return 自动续期锁选项
     */
    public static LockOptions autoRenew(Duration leaseTime, Duration maxRenewTime) {
        return builder()
                .waitTime(Duration.ZERO)
                .waitStrategy(LockWaitStrategy.NO_WAIT)
                .leaseTime(leaseTime)
                .autoRenew(maxRenewTime)
                .build();
    }

    /**
     * 创建带等待、租约和自动续期的锁选项。
     *
     * <p>
     * 适合长任务：
     * 先等待获取锁，获取成功后启动 watchdog 自动续期。
     * </p>
     *
     * @param waitTime 最长等待时间
     * @param leaseTime 锁租约时间
     * @param maxRenewTime 最大自动续期时间
     * @return 带等待和自动续期的锁选项
     */
    public static LockOptions waitForAndAutoRenew(Duration waitTime, Duration leaseTime, Duration maxRenewTime) {
        return builder()
                .waitTime(waitTime)
                .waitStrategy(LockWaitStrategy.BACKOFF)
                .leaseTime(leaseTime)
                .autoRenew(maxRenewTime)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 校验锁选项是否合法。
     *
     * <p>
     * 该方法在 build() 阶段自动调用。
     * </p>
     */
    public void validate() {
        if (namespace == null || namespace.isBlank()) {
            throw new InvalidLockOptionsException("namespace must not be blank");
        }

        if (waitTime == null || waitTime.isNegative()) {
            throw new InvalidLockOptionsException("waitTime must not be null or negative");
        }

        if (leaseTime == null || leaseTime.isZero() || leaseTime.isNegative()) {
            throw new InvalidLockOptionsException("leaseTime must be positive");
        }

        if (leaseTime.toMillis() <= 0) {
            throw new InvalidLockOptionsException("leaseTime must be at least 1 millisecond");
        }

        if (waitStrategy == null) {
            throw new InvalidLockOptionsException("waitStrategy must not be null");
        }

        if (waitTime.isZero() && waitStrategy != LockWaitStrategy.NO_WAIT) {
            throw new InvalidLockOptionsException("waitStrategy must be NO_WAIT when waitTime is zero");
        }

        if (!waitTime.isZero() && waitStrategy == LockWaitStrategy.NO_WAIT) {
            throw new InvalidLockOptionsException("waitStrategy must not be NO_WAIT when waitTime is greater than zero");
        }

        if (renewInterval == null || renewInterval.isZero() || renewInterval.isNegative()) {
            throw new InvalidLockOptionsException("renewInterval must be positive");
        }

        if (!renewInterval.minus(leaseTime).isNegative()) {
            throw new InvalidLockOptionsException("renewInterval must be less than leaseTime");
        }

        if (maxRenewTime == null || maxRenewTime.isZero() || maxRenewTime.isNegative()) {
            throw new InvalidLockOptionsException("maxRenewTime must be positive");
        }

        if (autoRenew && maxRenewTime.compareTo(leaseTime) < 0) {
            throw new InvalidLockOptionsException("maxRenewTime must be greater than or equal to leaseTime when autoRenew is enabled");
        }

        if (backoffSpec == null) {
            throw new InvalidLockOptionsException("backoffSpec must not be null");
        }
    }

    public String namespace() {
        return namespace;
    }

    public String getNamespace() {
        return namespace;
    }

    public Duration waitTime() {
        return waitTime;
    }

    public Duration getWaitTime() {
        return waitTime;
    }

    public Duration leaseTime() {
        return leaseTime;
    }

    public Duration getLeaseTime() {
        return leaseTime;
    }

    public LockWaitStrategy waitStrategy() {
        return waitStrategy;
    }

    public LockWaitStrategy getWaitStrategy() {
        return waitStrategy;
    }

    public boolean autoRenew() {
        return autoRenew;
    }

    public boolean isAutoRenew() {
        return autoRenew;
    }

    public Duration renewInterval() {
        return renewInterval;
    }

    public Duration getRenewInterval() {
        return renewInterval;
    }

    public Duration maxRenewTime() {
        return maxRenewTime;
    }

    public Duration getMaxRenewTime() {
        return maxRenewTime;
    }

    public boolean fencingRequired() {
        return fencingRequired;
    }

    public boolean isFencingRequired() {
        return fencingRequired;
    }

    public boolean failOnLockLost() {
        return failOnLockLost;
    }

    public boolean isFailOnLockLost() {
        return failOnLockLost;
    }

    public String providerName() {
        return providerName;
    }

    public String getProviderName() {
        return providerName;
    }

    public String fencingTokenProviderName() {
        return fencingTokenProviderName;
    }

    public String getFencingTokenProviderName() {
        return fencingTokenProviderName;
    }

    public RetryBackoffSpec backoffSpec() {
        return backoffSpec;
    }

    public RetryBackoffSpec getBackoffSpec() {
        return backoffSpec;
    }

    private static LockWaitStrategy inferWaitStrategy(Duration waitTime, LockWaitStrategy configured) {
        if (configured != null) {
            return configured;
        }

        if (waitTime == null || waitTime.isZero()) {
            return LockWaitStrategy.NO_WAIT;
        }

        return LockWaitStrategy.BACKOFF;
    }

    private static Duration defaultRenewInterval(Duration leaseTime) {
        long millis = Math.max(1L, leaseTime.toMillis() / 3L);
        return Duration.ofMillis(millis);
    }

    public static final class Builder {

        /** 锁命名空间。 */
        private String namespace = DEFAULT_NAMESPACE;

        /** 获取锁最长等待时间。 */
        private Duration waitTime = DEFAULT_WAIT_TIME;

        /** 锁租约时长。 */
        private Duration leaseTime = DEFAULT_LEASE_TIME;

        /** 获取锁等待策略。 */
        private LockWaitStrategy waitStrategy;

        /** 是否自动续租。 */
        private boolean autoRenew = DEFAULT_AUTO_RENEW;

        /** 自动续租间隔。 */
        private Duration renewInterval;

        /** 自动续租总时长上限。 */
        private Duration maxRenewTime = DEFAULT_MAX_RENEW_TIME;

        /** 是否要求 fencing token。 */
        private boolean fencingRequired = DEFAULT_FENCING_REQUIRED;

        /** 发现锁丢失时是否让业务失败。 */
        private boolean failOnLockLost = DEFAULT_FAIL_ON_LOCK_LOST;

        /** 指定的锁 Provider 名称。 */
        private String providerName;

        /** 指定的独立 fencing token Provider 名称。 */
        private String fencingTokenProviderName;

        /** 非原生等待时使用的重试退避策略。 */
        private RetryBackoffSpec backoffSpec = RetryBackoffSpec.defaults();

        private Builder() {
        }

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder waitTime(Duration waitTime) {
            this.waitTime = waitTime;
            return this;
        }

        public Builder leaseTime(Duration leaseTime) {
            this.leaseTime = leaseTime;
            return this;
        }

        public Builder waitStrategy(LockWaitStrategy waitStrategy) {
            this.waitStrategy = waitStrategy;
            return this;
        }

        public Builder autoRenew(boolean autoRenew) {
            this.autoRenew = autoRenew;
            return this;
        }

        /**
         * 开启自动续期，并设置最大自动续期时间。
         *
         * <p>
         * renewInterval 如果没有显式设置，会在 build 阶段推导为 leaseTime / 3。
         * </p>
         *
         * @param maxRenewTime 最大自动续期时间
         * @return builder
         */
        public Builder autoRenew(Duration maxRenewTime) {
            this.autoRenew = true;
            this.maxRenewTime = maxRenewTime;
            return this;
        }

        public Builder renewInterval(Duration renewInterval) {
            this.renewInterval = renewInterval;
            return this;
        }

        public Builder maxRenewTime(Duration maxRenewTime) {
            this.maxRenewTime = maxRenewTime;
            return this;
        }

        public Builder fencingRequired(boolean fencingRequired) {
            this.fencingRequired = fencingRequired;
            return this;
        }

        public Builder failOnLockLost(boolean failOnLockLost) {
            this.failOnLockLost = failOnLockLost;
            return this;
        }

        public Builder providerName(String providerName) {
            this.providerName = providerName;
            return this;
        }

        public Builder fencingTokenProviderName(String fencingTokenProviderName) {
            this.fencingTokenProviderName = fencingTokenProviderName;
            return this;
        }

        public Builder backoffSpec(RetryBackoffSpec backoffSpec) {
            this.backoffSpec = backoffSpec;
            return this;
        }

        public LockOptions build() {
            Duration actualWaitTime = waitTime == null ? DEFAULT_WAIT_TIME : waitTime;
            Duration actualLeaseTime = leaseTime == null ? DEFAULT_LEASE_TIME : leaseTime;
            LockWaitStrategy actualWaitStrategy = inferWaitStrategy(actualWaitTime, waitStrategy);
            Duration actualRenewInterval = renewInterval == null
                    ? defaultRenewInterval(actualLeaseTime)
                    : renewInterval;

            this.namespace = normalizeNamespace(namespace);
            this.waitTime = actualWaitTime;
            this.leaseTime = actualLeaseTime;
            this.waitStrategy = actualWaitStrategy;
            this.renewInterval = actualRenewInterval;
            this.maxRenewTime = maxRenewTime == null ? DEFAULT_MAX_RENEW_TIME : maxRenewTime;
            this.backoffSpec = backoffSpec == null ? RetryBackoffSpec.defaults() : backoffSpec;

            LockOptions options = new LockOptions(this);
            options.validate();
            return options;
        }

        private static String normalizeNamespace(String namespace) {
            if (namespace == null || namespace.isBlank()) {
                return DEFAULT_NAMESPACE;
            }
            return namespace.trim();
        }
    }

    @Override
    public String toString() {
        return "LockOptions{" +
                "namespace='" + namespace + '\'' +
                ", waitTime=" + waitTime +
                ", leaseTime=" + leaseTime +
                ", waitStrategy=" + waitStrategy +
                ", autoRenew=" + autoRenew +
                ", renewInterval=" + renewInterval +
                ", maxRenewTime=" + maxRenewTime +
                ", fencingRequired=" + fencingRequired +
                ", failOnLockLost=" + failOnLockLost +
                ", providerName='" + providerName + '\'' +
                ", fencingTokenProviderName='" + fencingTokenProviderName + '\'' +
                ", backoffSpec=" + backoffSpec +
                '}';
    }
}
