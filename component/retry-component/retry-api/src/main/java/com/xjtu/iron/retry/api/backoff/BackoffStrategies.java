package com.xjtu.iron.retry.api.backoff;

import com.xjtu.iron.retry.api.execution.RetryAttempt;
import com.xjtu.iron.retry.api.policy.RetryDecision;
import com.xjtu.iron.retry.api.policy.RetryFailureCategory;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/** 提供常用、无状态且线程安全的退避策略工厂。 */
public final class BackoffStrategies {

    /** 共享的无退避策略。 */
    private static final BackoffStrategy NO_BACKOFF =
            (attempt, decision) -> RetryDelay.none();

    private BackoffStrategies() {
    }

    /** 返回不执行退避等待的共享策略。 */
    public static BackoffStrategy none() {
        return NO_BACKOFF;
    }

    /** 创建固定退避策略。 */
    public static BackoffStrategy fixed(Duration delay) {
        Duration actualDelay = requireNonNegative(delay, "delay");
        return (attempt, decision) -> RetryDelay.of(
                actualDelay,
                RetryDelaySource.FIXED,
                "fixed backoff"
        );
    }

    /** 创建不带随机抖动的指数退避策略。 */
    public static BackoffStrategy exponential(
            Duration initialDelay,
            Duration maxDelay,
            double multiplier) {
        return new ExponentialBackoffStrategy(
                initialDelay,
                maxDelay,
                multiplier,
                null
        );
    }

    /** 使用 ThreadLocalRandom 创建全抖动指数退避策略。 */
    public static BackoffStrategy exponentialWithFullJitter(
            Duration initialDelay,
            Duration maxDelay,
            double multiplier) {
        return exponentialWithFullJitter(
                initialDelay,
                maxDelay,
                multiplier,
                ThreadLocalRandom.current()
        );
    }

    /**
     * 使用调用方提供的随机源创建全抖动指数退避策略。
     *
     * <p>该重载使随机退避可以在单元测试中使用固定种子重现。</p>
     */
    public static BackoffStrategy exponentialWithFullJitter(
            Duration initialDelay,
            Duration maxDelay,
            double multiplier,
            RandomGenerator randomGenerator) {
        return new ExponentialBackoffStrategy(
                initialDelay,
                maxDelay,
                multiplier,
                Objects.requireNonNull(randomGenerator, "randomGenerator must not be null")
        );
    }

    /** 按失败类别选择委托退避策略，未匹配时使用默认策略。 */
    public static BackoffStrategy categoryAware(
            Map<RetryFailureCategory, BackoffStrategy> strategies,
            BackoffStrategy defaultStrategy) {
        Objects.requireNonNull(strategies, "strategies must not be null");
        BackoffStrategy actualDefault = Objects.requireNonNull(
                defaultStrategy,
                "defaultStrategy must not be null"
        );
        Map<RetryFailureCategory, BackoffStrategy> copiedStrategies =
                new EnumMap<>(RetryFailureCategory.class);
        strategies.forEach((category, strategy) -> copiedStrategies.put(
                Objects.requireNonNull(category, "category must not be null"),
                Objects.requireNonNull(strategy, "strategy must not be null")
        ));
        return (attempt, decision) -> {
            RetryDecision actualDecision = Objects.requireNonNull(
                    decision,
                    "decision must not be null"
            );
            BackoffStrategy selectedStrategy = copiedStrategies.getOrDefault(
                    actualDecision.getFailureCategory(),
                    actualDefault
            );
            RetryDelay selectedDelay = Objects.requireNonNull(
                    selectedStrategy.nextDelay(attempt, actualDecision),
                    "selected strategy returned null"
            );
            return RetryDelay.of(
                    selectedDelay.getDuration(),
                    selectedDelay.getSource(),
                    "category=" + actualDecision.getFailureCategory()
                            + ", delegatedSource=" + selectedDelay.getSource()
                            + ", delegatedReason=" + selectedDelay.getReason()
            );
        };
    }

    /** 实现指数退避和可选全抖动。 */
    private static final class ExponentialBackoffStrategy implements BackoffStrategy {

        /** 初始等待纳秒数。 */
        private final long initialDelayNanos;
        /** 最大等待纳秒数。 */
        private final long maxDelayNanos;
        /** 每次增长倍数。 */
        private final double multiplier;
        /** 可选随机源；为空表示不使用抖动。 */
        private final RandomGenerator randomGenerator;

        private ExponentialBackoffStrategy(
                Duration initialDelay,
                Duration maxDelay,
                double multiplier,
                RandomGenerator randomGenerator) {
            Duration actualInitial = requireNonNegative(initialDelay, "initialDelay");
            Duration actualMax = requireNonNegative(maxDelay, "maxDelay");
            if (actualInitial.compareTo(actualMax) > 0) {
                throw new IllegalArgumentException(
                        "initialDelay must not be greater than maxDelay"
                );
            }
            if (!Double.isFinite(multiplier) || multiplier < 1.0D) {
                throw new IllegalArgumentException(
                        "multiplier must be finite and greater than or equal to 1.0"
                );
            }
            this.initialDelayNanos = toNanosExact(actualInitial, "initialDelay");
            this.maxDelayNanos = toNanosExact(actualMax, "maxDelay");
            this.multiplier = multiplier;
            this.randomGenerator = randomGenerator;
        }

        /** 根据已完成尝试编号计算下一次等待时间。 */
        @Override
        public RetryDelay nextDelay(RetryAttempt<?> attempt, RetryDecision decision) {
            RetryAttempt<?> actualAttempt = Objects.requireNonNull(
                    attempt,
                    "attempt must not be null"
            );
            Objects.requireNonNull(decision, "decision must not be null");
            long upperBound = calculateUpperBound(actualAttempt.getAttemptNumber());
            if (randomGenerator == null || upperBound == 0L) {
                return RetryDelay.of(
                        Duration.ofNanos(upperBound),
                        RetryDelaySource.EXPONENTIAL,
                        "exponential backoff"
                );
            }
            long randomizedDelay = randomInclusive(randomGenerator, upperBound);
            return RetryDelay.of(
                    Duration.ofNanos(randomizedDelay),
                    RetryDelaySource.FULL_JITTER,
                    "full jitter exponential backoff"
            );
        }

        /** 计算并限制当前指数退避上限。 */
        private long calculateUpperBound(int completedAttemptNumber) {
            int exponent = Math.max(0, completedAttemptNumber - 1);
            double calculated = initialDelayNanos * Math.pow(multiplier, exponent);
            if (!Double.isFinite(calculated) || calculated >= maxDelayNanos) {
                return maxDelayNanos;
            }
            return Math.max(0L, (long) calculated);
        }

        /** 在零到上限之间生成包含两端的随机纳秒数。 */
        private static long randomInclusive(RandomGenerator randomGenerator, long upperBound) {
            if (upperBound == Long.MAX_VALUE) {
                return randomGenerator.nextLong(Long.MAX_VALUE);
            }
            return randomGenerator.nextLong(upperBound + 1L);
        }
    }

    /** 校验 Duration 非空且非负。 */
    private static Duration requireNonNegative(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return value;
    }

    /** 将 Duration 精确转换为纳秒并转换溢出异常。 */
    private static long toNanosExact(Duration duration, String name) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + " is too large", exception);
        }
    }
}
