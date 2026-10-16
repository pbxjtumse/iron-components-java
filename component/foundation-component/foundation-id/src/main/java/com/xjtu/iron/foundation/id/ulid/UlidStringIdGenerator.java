package com.xjtu.iron.foundation.id.ulid;

import com.xjtu.iron.foundation.id.api.IdGenerationException;
import com.xjtu.iron.foundation.id.api.StringIdGenerator;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Arrays;
import java.util.Objects;

/**
 * 生成 26 位 Crockford Base32 编码的单调 ULID。
 *
 * <p>标识由 48 位毫秒时间戳和 80 位随机数组成。同一生成器实例在同一毫秒内递增随机部分，
 * 从而保持字符串字典序单调。</p>
 */
public final class UlidStringIdGenerator implements StringIdGenerator {

    private static final long MAX_TIMESTAMP = 0x0000FFFFFFFFFFFFL;
    private static final char[] ALPHABET =
            "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    /** 提供 ULID 的毫秒时间部分。 */
    private final Clock clock;

    /** 初始化 80 位随机部分的安全随机源。 */
    private final SecureRandom secureRandom;

    /** 最近一次使用的 80 位随机状态。 */
    private final byte[] randomness = new byte[10];

    /** 最近一次实际或逻辑使用的时间戳。 */
    private long lastTimestamp = -1L;

    public UlidStringIdGenerator() {
        this(Clock.systemUTC(), new SecureRandom());
    }

    public UlidStringIdGenerator(Clock clock, SecureRandom secureRandom) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.secureRandom = Objects.requireNonNull(
                secureRandom,
                "secureRandom must not be null"
        );
    }

    @Override
    public synchronized String nextId() {
        long timestamp = validateTimestamp(clock.millis());
        if (timestamp > lastTimestamp) {
            secureRandom.nextBytes(randomness);
        } else {
            timestamp = lastTimestamp;
            if (!incrementRandomness()) {
                timestamp = validateTimestamp(lastTimestamp + 1L);
                Arrays.fill(randomness, (byte) 0);
            }
        }

        lastTimestamp = timestamp;
        return encode(timestamp, randomness);
    }

    private long validateTimestamp(long timestamp) {
        if (timestamp < 0L) {
            throw new IdGenerationException("ULID timestamp must not be negative");
        }
        if (timestamp > MAX_TIMESTAMP) {
            throw new IdGenerationException("ULID timestamp exceeds 48-bit range");
        }
        return timestamp;
    }

    private boolean incrementRandomness() {
        for (int index = randomness.length - 1; index >= 0; index--) {
            int value = (randomness[index] & 0xFF) + 1;
            randomness[index] = (byte) value;
            if (value <= 0xFF) {
                return true;
            }
        }
        return false;
    }

    private String encode(long timestamp, byte[] randomBytes) {
        char[] encoded = new char[26];
        long timestampValue = timestamp;
        for (int index = 9; index >= 0; index--) {
            encoded[index] = ALPHABET[(int) (timestampValue & 31L)];
            timestampValue >>>= 5;
        }

        int outputIndex = 10;
        int buffer = 0;
        int bufferedBits = 0;
        for (byte randomByte : randomBytes) {
            buffer = (buffer << 8) | (randomByte & 0xFF);
            bufferedBits += 8;
            while (bufferedBits >= 5) {
                bufferedBits -= 5;
                encoded[outputIndex++] = ALPHABET[(buffer >>> bufferedBits) & 31];
            }
            buffer = bufferedBits == 0 ? 0 : buffer & ((1 << bufferedBits) - 1);
        }
        return new String(encoded);
    }
}
