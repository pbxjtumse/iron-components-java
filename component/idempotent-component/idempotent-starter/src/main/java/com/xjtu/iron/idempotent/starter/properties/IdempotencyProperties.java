package com.xjtu.iron.idempotent.starter.properties;

import com.xjtu.iron.idempotent.api.policy.*;
import com.xjtu.iron.idempotent.api.recovery.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 分布式幂等组件配置。
 *
 * <p>组件级提供 WINDOWED / DURABLE 默认策略，并支持通过 policies 定义命名 Policy。</p>
 */
@ConfigurationProperties(prefix = "xjtu.iron.idempotent")
public class IdempotencyProperties {

    /** 总开关，关闭后不装配幂等组件运行时。 */
    private boolean enabled = true;

    /** 未在请求中指定 policyName/inline policy 时使用的默认命名策略。 */
    private String defaultPolicy = "durable-default";

    /** WINDOWED 默认 Repository，Starter 默认指向 Redis。 */
    private String defaultWindowedRepository = "redis";

    /** DURABLE 默认 Repository，Starter 默认指向 JDBC。 */
    private String defaultDurableRepository = "jdbc";

    /** 全局默认 PROCESSING 租约时长，命名 Policy 未覆盖时使用。 */
    private Duration processingTimeout = Duration.ofSeconds(30);

    /** WINDOWED 默认策略参数。 */
    private final Windowed windowed = new Windowed();

    /** DURABLE 默认策略参数。 */
    private final Durable durable = new Durable();

    /** 全局短锁默认配置。 */
    private final Lock lock = new Lock();

    /** 事务集成配置。 */
    private final Transaction transaction = new Transaction();

    /** Redis Provider 配置。 */
    private final Redis redis = new Redis();

    /** JDBC Provider 配置。 */
    private final Jdbc jdbc = new Jdbc();

    /** 用户自定义命名策略。 */
    private final Map<String, Policy> policies = new LinkedHashMap<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getDefaultPolicy() { return defaultPolicy; }
    public void setDefaultPolicy(String defaultPolicy) { this.defaultPolicy = defaultPolicy; }
    public String getDefaultWindowedRepository() { return defaultWindowedRepository; }
    public void setDefaultWindowedRepository(String value) { this.defaultWindowedRepository = value; }
    public String getDefaultDurableRepository() { return defaultDurableRepository; }
    public void setDefaultDurableRepository(String value) { this.defaultDurableRepository = value; }
    public Duration getProcessingTimeout() { return processingTimeout; }
    public void setProcessingTimeout(Duration processingTimeout) { this.processingTimeout = processingTimeout; }
    public Windowed getWindowed() { return windowed; }

    public Durable getDurable() { return durable; }
    public Lock getLock() { return lock; }
    public Transaction getTransaction() { return transaction; }
    public Redis getRedis() { return redis; }
    public Jdbc getJdbc() { return jdbc; }
    public Map<String, Policy> getPolicies() { return policies; }

    public static class Windowed {
        /** 同 key 在该时间内属于同一个逻辑请求。 */
        private Duration idempotencyWindow = Duration.ofMinutes(10);

        /** 窗口起点/推进方式，默认从首次 acquire 固定计算。 */
        private IdempotencyWindowPolicy windowPolicy =
                IdempotencyWindowPolicy.FIXED_FROM_FIRST_ACQUIRE;

        /** 窗口结束后的额外物理保留时间。 */
        private Duration recordRetentionTtl = Duration.ZERO;

        /** WINDOWED 默认不启用外部恢复。 */
        private IdempotencyRecoveryMode recoveryMode = IdempotencyRecoveryMode.NONE;

        /** 是否允许恢复超时 PROCESSING。 */
        private boolean recoverProcessingTimeout;

        /** 是否允许恢复 retryable FAILED。 */
        private boolean recoverFailed;

        public Duration getIdempotencyWindow() { return idempotencyWindow; }
        public void setIdempotencyWindow(Duration value) { this.idempotencyWindow = value; }
        public IdempotencyWindowPolicy getWindowPolicy() { return windowPolicy; }
        public void setWindowPolicy(IdempotencyWindowPolicy value) { this.windowPolicy = value; }
        public Duration getRecordRetentionTtl() { return recordRetentionTtl; }
        public void setRecordRetentionTtl(Duration value) { this.recordRetentionTtl = value; }
        public IdempotencyRecoveryMode getRecoveryMode() { return recoveryMode; }
        public void setRecoveryMode(IdempotencyRecoveryMode value) { this.recoveryMode = value; }
        public boolean isRecoverProcessingTimeout() { return recoverProcessingTimeout; }
        public void setRecoverProcessingTimeout(boolean value) { this.recoverProcessingTimeout = value; }
        public boolean isRecoverFailed() { return recoverFailed; }
        public void setRecoverFailed(boolean value) { this.recoverFailed = value; }
    }

    public static class Durable {
        /** DURABLE 默认允许外部可靠任务恢复异常 generation。 */
        private IdempotencyRecoveryMode recoveryMode = IdempotencyRecoveryMode.EXTERNAL_TASK;

        /** 是否允许恢复超时 PROCESSING。 */
        private boolean recoverProcessingTimeout = true;

        /** 是否允许恢复 retryable FAILED。 */
        private boolean recoverFailed = true;

        public IdempotencyRecoveryMode getRecoveryMode() { return recoveryMode; }
        public void setRecoveryMode(IdempotencyRecoveryMode value) { this.recoveryMode = value; }
        public boolean isRecoverProcessingTimeout() { return recoverProcessingTimeout; }
        public void setRecoverProcessingTimeout(boolean value) { this.recoverProcessingTimeout = value; }
        public boolean isRecoverFailed() { return recoverFailed; }
        public void setRecoverFailed(boolean value) { this.recoverFailed = value; }
    }

    /** 全局默认短锁配置；命名 Policy 可以选择是否覆盖。 */
    public static class Lock {
        /** 是否启用短锁；关闭时直接依赖 Repository 原子语义。 */
        private boolean enabled = false;

        /** 分布式锁 Provider 名称，空值表示使用 lock-component 默认 Provider。 */
        private String providerName;

        /** 等待拿锁时间；0 表示 NO_WAIT。 */
        private Duration waitTime = Duration.ZERO;

        /** 短锁租约时长，只覆盖状态抢占，不覆盖业务执行。 */
        private Duration leaseTime = Duration.ofSeconds(5);

        /** 锁不可用时是否退化到 Repository 原子状态判断。 */
        private boolean fallbackToStateOnFailure = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getProviderName() { return providerName; }
        public void setProviderName(String providerName) { this.providerName = providerName; }
        public Duration getWaitTime() { return waitTime; }
        public void setWaitTime(Duration waitTime) { this.waitTime = waitTime; }
        public Duration getLeaseTime() { return leaseTime; }
        public void setLeaseTime(Duration leaseTime) { this.leaseTime = leaseTime; }
        public boolean isFallbackToStateOnFailure() { return fallbackToStateOnFailure; }
        public void setFallbackToStateOnFailure(boolean value) { this.fallbackToStateOnFailure = value; }
    }

    public static class Transaction {
        /** 是否尝试接入 transaction-component。 */
        private boolean enabled = true;

        /** true 时若缺少 TransactionExecutor 直接启动失败，避免误以为有 Tx-B 闭环。 */
        private boolean requireTemplate = false;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isRequireTemplate() { return requireTemplate; }
        public void setRequireTemplate(boolean requireTemplate) { this.requireTemplate = requireTemplate; }
    }

    /**
     * 自定义命名 Policy。
     *
     * <p>null 字段会继承组件默认值；lockEnabled=null 表示继承全局 lock.enabled。</p>
     */
    public static class Policy {
        /** Policy 模式；为空时按命名默认推导。 */
        private IdempotencyMode mode;

        /** 业务隔离域，默认 default。 */
        private String namespace = IdempotencyPolicy.DEFAULT_NAMESPACE;

        /** 显式 Repository 名称；为空时按 mode 使用全局默认 Repository。 */
        private String repositoryName;

        /** 当前 PROCESSING generation 的租约时长。 */
        private Duration processingTimeout;

        /** WINDOWED 语义窗口时长。 */
        private Duration idempotencyWindow;

        /** WINDOWED 窗口策略。 */
        private IdempotencyWindowPolicy windowPolicy;

        /** WINDOWED 记录保留 TTL。 */
        private Duration recordRetentionTtl;

        /** 该 Policy 的恢复模式。 */
        private IdempotencyRecoveryMode recoveryMode;

        /** 是否恢复超时 PROCESSING；null 表示继承模式默认值。 */
        private Boolean recoverProcessingTimeout;

        /** 是否恢复 retryable FAILED；null 表示继承模式默认值。 */
        private Boolean recoverFailed;

        /** 是否覆盖全局短锁开关；null 表示继承。 */
        private Boolean lockEnabled;

        /** 是否覆盖全局短锁 Provider。 */
        private String lockProviderName;

        /** 是否覆盖全局短锁等待时间。 */
        private Duration lockWaitTime;

        /** 是否覆盖全局短锁租约时间。 */
        private Duration lockLeaseTime;

        /** 是否覆盖全局锁失败 fallback 策略。 */
        private Boolean lockFallbackToStateOnFailure;

        public IdempotencyMode getMode() { return mode; }
        public void setMode(IdempotencyMode mode) { this.mode = mode; }
        public String getNamespace() { return namespace; }
        public void setNamespace(String namespace) { this.namespace = namespace; }
        public String getRepositoryName() { return repositoryName; }
        public void setRepositoryName(String repositoryName) { this.repositoryName = repositoryName; }
        public Duration getProcessingTimeout() { return processingTimeout; }
        public void setProcessingTimeout(Duration processingTimeout) { this.processingTimeout = processingTimeout; }
        public Duration getIdempotencyWindow() { return idempotencyWindow; }
        public void setIdempotencyWindow(Duration idempotencyWindow) { this.idempotencyWindow = idempotencyWindow; }
        public IdempotencyWindowPolicy getWindowPolicy() { return windowPolicy; }
        public void setWindowPolicy(IdempotencyWindowPolicy windowPolicy) { this.windowPolicy = windowPolicy; }
        public Duration getRecordRetentionTtl() { return recordRetentionTtl; }
        public void setRecordRetentionTtl(Duration recordRetentionTtl) { this.recordRetentionTtl = recordRetentionTtl; }
        public IdempotencyRecoveryMode getRecoveryMode() { return recoveryMode; }
        public void setRecoveryMode(IdempotencyRecoveryMode recoveryMode) { this.recoveryMode = recoveryMode; }
        public Boolean getRecoverProcessingTimeout() { return recoverProcessingTimeout; }
        public void setRecoverProcessingTimeout(Boolean recoverProcessingTimeout) {
            this.recoverProcessingTimeout = recoverProcessingTimeout;
        }
        public Boolean getRecoverFailed() { return recoverFailed; }
        public void setRecoverFailed(Boolean recoverFailed) { this.recoverFailed = recoverFailed; }

        public Boolean getLockEnabled() { return lockEnabled; }
        public void setLockEnabled(Boolean lockEnabled) { this.lockEnabled = lockEnabled; }
        public String getLockProviderName() { return lockProviderName; }
        public void setLockProviderName(String lockProviderName) { this.lockProviderName = lockProviderName; }
        public Duration getLockWaitTime() { return lockWaitTime; }
        public void setLockWaitTime(Duration lockWaitTime) { this.lockWaitTime = lockWaitTime; }
        public Duration getLockLeaseTime() { return lockLeaseTime; }
        public void setLockLeaseTime(Duration lockLeaseTime) { this.lockLeaseTime = lockLeaseTime; }
        public Boolean getLockFallbackToStateOnFailure() { return lockFallbackToStateOnFailure; }
        public void setLockFallbackToStateOnFailure(Boolean value) {
            this.lockFallbackToStateOnFailure = value;
        }
    }

    public static class Redis {
        /** 是否装配 Redis Repository。 */
        private boolean enabled = true;

        /** Redis 幂等 Key 前缀，最终 key 还会包含 storeName/namespace/logicalKey。 */
        private String keyPrefix = "iron:idempotency";
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getKeyPrefix() { return keyPrefix; }
        public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
    }

    public static class Jdbc {
        /** 是否装配 JDBC Repository。 */
        private boolean enabled = true;

        /** 幂等记录表名，只允许字母、数字和下划线。 */
        private String tableName = "iron_idempotency_record";
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
    }
}
