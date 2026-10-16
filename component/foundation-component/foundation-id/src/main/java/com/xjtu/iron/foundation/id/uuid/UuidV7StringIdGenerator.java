package com.xjtu.iron.foundation.id.uuid;

import com.xjtu.iron.foundation.id.api.IdGenerationException;
import com.xjtu.iron.foundation.id.api.StringIdGenerator;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * 生成 RFC 9562 UUID v7。
 *
 * <p>同一生成器实例使用逻辑时间和 74 位随机状态保持单调；跨进程唯一性依赖时间与随机空间，
 * 不承诺多个 JVM 之间严格全局递增。</p>
 */
public final class UuidV7StringIdGenerator implements StringIdGenerator {

    private static final long MAX_TIMESTAMP = 0x0000FFFFFFFFFFFFL;
    private static final int MAX_RANDOM_A = 0x0FFF;
    private static final long MAX_RANDOM_B = 0x3FFFFFFFFFFFFFFFL;

    /** 提供 48 位 Unix 毫秒时间。 */
    private final Clock clock;

    /** 初始化随机状态的安全随机源。 */
    private final SecureRandom secureRandom;

    /** 上一次实际或逻辑使用的时间戳。 */
    private long lastTimestamp = -1L;

    /** UUID v7 的 12 位 rand_a。 */
    private int randomA;

    /** UUID v7 的 62 位 rand_b。 */
    private long randomB;

    public UuidV7StringIdGenerator() {
        this(Clock.systemUTC(), new SecureRandom());
    }

    public UuidV7StringIdGenerator(Clock clock, SecureRandom secureRandom) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.secureRandom = Objects.requireNonNull(
                secureRandom,
                "secureRandom must not be null"
        );
    }

    @Override
    public synchronized String nextId() {
        return nextUuid().toString();
    }

    /**
     * 直接生成 UUID 对象，便于数据库 UUID 字段或协议层使用。
     *
     * @return UUID v7
     */
    public synchronized UUID nextUuid() {
        long timestamp = validateTimestamp(clock.millis());
        if (timestamp > lastTimestamp) {
            seedRandomState();
        } else {
            timestamp = lastTimestamp;
            if (!incrementRandomState()) {
                timestamp = validateTimestamp(lastTimestamp + 1L);
                randomA = 0;
                randomB = 0L;
            }
        }

        lastTimestamp = timestamp;
        long mostSignificantBits = (timestamp << 16)
                | 0x7000L
                | (randomA & MAX_RANDOM_A);
        long leastSignificantBits = 0x8000000000000000L
                | (randomB & MAX_RANDOM_B);
        return new UUID(mostSignificantBits, leastSignificantBits);
    }

    private long validateTimestamp(long timestamp) {
        if (timestamp < 0L) {
            throw new IdGenerationException("UUID v7 timestamp must not be negative");
        }
        if (timestamp > MAX_TIMESTAMP) {
            throw new IdGenerationException("UUID v7 timestamp exceeds 48-bit range");
        }
        return timestamp;
    }

    private void seedRandomState() {
        randomA = secureRandom.nextInt(MAX_RANDOM_A + 1);
        randomB = secureRandom.nextLong() & MAX_RANDOM_B;
    }

    private boolean incrementRandomState() {
        if (randomB < MAX_RANDOM_B) {
            randomB++;
            return true;
        }
        randomB = 0L;
        if (randomA < MAX_RANDOM_A) {
            randomA++;
            return true;
        }
        return false;
    }
}
