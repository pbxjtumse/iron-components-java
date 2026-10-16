package com.xjtu.iron.foundation.id.snowflake;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Snowflake 节点、纪元、时钟回拨与序列耗尽配置。 */
public final class SnowflakeOptions {

    public static final long DEFAULT_EPOCH_MILLIS =
            Instant.parse("2024-01-01T00:00:00Z").toEpochMilli();
    public static final Duration DEFAULT_SEQUENCE_WAIT_TIMEOUT = Duration.ofMillis(10L);

    /** Snowflake 时间差计算使用的自定义纪元。 */
    private final long epochMillis;

    /** 由部署系统保证唯一的 10 位节点编号。 */
    private final long workerId;

    /** 系统时钟回拨时采用的处理策略。 */
    private final ClockRollbackStrategy clockRollbackStrategy;

    /** 逻辑时间模式允许吸收的最大时钟回拨毫秒数。 */
    private final long maxBackwardMillis;

    /** 同毫秒序列耗尽后等待真实时钟推进的最长时间。 */
    private final Duration sequenceWaitTimeout;

    private SnowflakeOptions(Builder builder) {
        if (builder.workerId < 0L || builder.workerId > 1023L) {
            throw new IllegalArgumentException("workerId must be between 0 and 1023");
        }
        if (builder.epochMillis < 0L) {
            throw new IllegalArgumentException("epochMillis must not be negative");
        }
        if (builder.maxBackwardMillis < 0L) {
            throw new IllegalArgumentException("maxBackwardMillis must not be negative");
        }
        Duration actualWaitTimeout = Objects.requireNonNull(
                builder.sequenceWaitTimeout,
                "sequenceWaitTimeout must not be null"
        );
        if (actualWaitTimeout.isNegative()) {
            throw new IllegalArgumentException("sequenceWaitTimeout must not be negative");
        }
        this.epochMillis = builder.epochMillis;
        this.workerId = builder.workerId;
        this.clockRollbackStrategy = Objects.requireNonNull(
                builder.clockRollbackStrategy,
                "clockRollbackStrategy must not be null"
        );
        this.maxBackwardMillis = builder.maxBackwardMillis;
        this.sequenceWaitTimeout = actualWaitTimeout;
    }

    public static Builder builder(long workerId) {
        return new Builder(workerId);
    }

    public long getEpochMillis() {
        return epochMillis;
    }

    public long getWorkerId() {
        return workerId;
    }

    public ClockRollbackStrategy getClockRollbackStrategy() {
        return clockRollbackStrategy;
    }

    public long getMaxBackwardMillis() {
        return maxBackwardMillis;
    }

    public Duration getSequenceWaitTimeout() {
        return sequenceWaitTimeout;
    }

    public static final class Builder {

        private final long workerId;
        private long epochMillis = DEFAULT_EPOCH_MILLIS;
        private ClockRollbackStrategy clockRollbackStrategy =
                ClockRollbackStrategy.FAIL_FAST;
        private long maxBackwardMillis;
        private Duration sequenceWaitTimeout = DEFAULT_SEQUENCE_WAIT_TIMEOUT;

        private Builder(long workerId) {
            this.workerId = workerId;
        }

        public Builder epochMillis(long epochMillis) {
            this.epochMillis = epochMillis;
            return this;
        }

        public Builder clockRollbackStrategy(
                ClockRollbackStrategy clockRollbackStrategy) {
            this.clockRollbackStrategy = clockRollbackStrategy;
            return this;
        }

        public Builder maxBackwardMillis(long maxBackwardMillis) {
            this.maxBackwardMillis = maxBackwardMillis;
            return this;
        }

        public Builder sequenceWaitTimeout(Duration sequenceWaitTimeout) {
            this.sequenceWaitTimeout = sequenceWaitTimeout;
            return this;
        }

        public SnowflakeOptions build() {
            return new SnowflakeOptions(this);
        }
    }
}
