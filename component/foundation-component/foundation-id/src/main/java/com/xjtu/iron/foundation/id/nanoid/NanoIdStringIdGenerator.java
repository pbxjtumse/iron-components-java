package com.xjtu.iron.foundation.id.nanoid;

import com.xjtu.iron.foundation.id.api.StringIdGenerator;

import java.security.SecureRandom;
import java.util.Objects;

/** 使用安全随机数和无模偏差拒绝采样生成 URL 友好短标识。 */
public final class NanoIdStringIdGenerator implements StringIdGenerator {

    /** 实际参与随机选择的字符表。 */
    private final char[] alphabet;

    /** 输出标识长度。 */
    private final int size;

    /** 生成随机字节的安全随机源。 */
    private final SecureRandom secureRandom;

    /** 将随机字节限制到最接近字符表大小的二进制掩码。 */
    private final int mask;

    /** 每轮生成的随机字节数量。 */
    private final int step;

    public NanoIdStringIdGenerator() {
        this(NanoIdOptions.defaults(), new SecureRandom());
    }

    public NanoIdStringIdGenerator(NanoIdOptions options) {
        this(options, new SecureRandom());
    }

    public NanoIdStringIdGenerator(
            NanoIdOptions options,
            SecureRandom secureRandom) {
        NanoIdOptions actualOptions = Objects.requireNonNull(
                options,
                "options must not be null"
        );
        this.alphabet = actualOptions.getAlphabet().toCharArray();
        this.size = actualOptions.getSize();
        this.secureRandom = Objects.requireNonNull(
                secureRandom,
                "secureRandom must not be null"
        );
        this.mask = calculateMask(alphabet.length);
        this.step = Math.max(
                1,
                (int) Math.ceil(1.6D * mask * size / alphabet.length)
        );
    }

    @Override
    public String nextId() {
        if (alphabet.length == 1) {
            return String.valueOf(alphabet[0]).repeat(size);
        }

        StringBuilder id = new StringBuilder(size);
        byte[] bytes = new byte[step];
        while (id.length() < size) {
            secureRandom.nextBytes(bytes);
            for (byte randomByte : bytes) {
                int alphabetIndex = randomByte & mask;
                if (alphabetIndex < alphabet.length) {
                    id.append(alphabet[alphabetIndex]);
                    if (id.length() == size) {
                        return id.toString();
                    }
                }
            }
        }
        return id.toString();
    }

    private static int calculateMask(int alphabetSize) {
        if (alphabetSize == 1) {
            return 0;
        }
        int bits = 32 - Integer.numberOfLeadingZeros(alphabetSize - 1);
        return (1 << bits) - 1;
    }
}
