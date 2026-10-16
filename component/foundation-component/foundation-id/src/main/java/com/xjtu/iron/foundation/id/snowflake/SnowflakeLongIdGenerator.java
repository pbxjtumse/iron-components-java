package com.xjtu.iron.foundation.id.snowflake;

import com.xjtu.iron.foundation.id.api.IdGenerationException;
import com.xjtu.iron.foundation.id.api.LongIdGenerator;

import java.time.Clock;
import java.util.Objects;

/**
 * 生成 64 位 Snowflake 标识。
 *
 * <p>位布局为 1 位符号位、41 位毫秒时间、10 位 workerId 和 12 位同毫秒序列。
 * workerId 必须由部署系统保证唯一。</p>
 */
public final class SnowflakeLongIdGenerator implements LongIdGenerator {

    private static final long MAX_TIMESTAMP_DELTA = (1L << 41) - 1L;
    private static final long SEQUENCE_MASK = (1L << 12) - 1L;
    private static final int WORKER_ID_SHIFT = 12;
    private static final int TIMESTAMP_SHIFT = 22;

    /** 节点、纪元和时钟策略配置。 */
    private final SnowflakeOptions options;

    /** 提供生成标识时使用的毫秒时间。 */
    private final Clock clock;

    /** 序列耗尽后等待真实时间推进的纳秒预算。 */
    private final long sequenceWaitTimeoutNanos;

    /** 最近一次实际或逻辑使用的毫秒时间。 */
    private long lastTimestamp = -1L;

    /** 当前毫秒内已经使用的 12 位序列。 */
    private long sequence;

    public SnowflakeLongIdGenerator(SnowflakeOptions options) {
        this(options, Clock.systemUTC());
    }

    public SnowflakeLongIdGenerator(SnowflakeOptions options, Clock clock) {
        this.options = Objects.requireNonNull(options, "options must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        try {
            this.sequenceWaitTimeoutNanos = options.getSequenceWaitTimeout().toNanos();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "sequenceWaitTimeout is too large",
                    exception
            );
        }
    }

    @Override
    public synchronized long nextLongId() {
        long timestamp = resolveTimestamp(clock.millis());
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1L) & SEQUENCE_MASK;
            if (sequence == 0L) {
                timestamp = nextTimestampAfterSequenceOverflow(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        long timestampDelta = timestamp - options.getEpochMillis();
        if (timestampDelta < 0L) {
            throw new IdGenerationException("current time is before Snowflake epoch");
        }
        if (timestampDelta > MAX_TIMESTAMP_DELTA) {
            throw new IdGenerationException("Snowflake timestamp space is exhausted");
        }

        lastTimestamp = timestamp;
        return (timestampDelta << TIMESTAMP_SHIFT)
                | (options.getWorkerId() << WORKER_ID_SHIFT)
                | sequence;
    }

    private long resolveTimestamp(long currentTimestamp) {
        if (currentTimestamp >= lastTimestamp) {
            return currentTimestamp;
        }

        long backwardMillis = lastTimestamp - currentTimestamp;
        if (options.getClockRollbackStrategy() == ClockRollbackStrategy.FAIL_FAST
                || backwardMillis > options.getMaxBackwardMillis()) {
            throw new IdGenerationException(
                    "clock moved backwards by " + backwardMillis + " ms"
            );
        }
        return lastTimestamp;
    }

    private long nextTimestampAfterSequenceOverflow(long currentLastTimestamp) {
        if (options.getClockRollbackStrategy() == ClockRollbackStrategy.USE_LOGICAL_TIME) {
            return currentLastTimestamp + 1L;
        }

        long startedAt = System.nanoTime();
        long timestamp = clock.millis();
        while (timestamp <= currentLastTimestamp) {
            if (System.nanoTime() - startedAt >= sequenceWaitTimeoutNanos) {
                throw new IdGenerationException(
                        "Snowflake sequence exhausted before clock advanced"
                );
            }
            Thread.onSpinWait();
            timestamp = clock.millis();
        }
        return timestamp;
    }
}
