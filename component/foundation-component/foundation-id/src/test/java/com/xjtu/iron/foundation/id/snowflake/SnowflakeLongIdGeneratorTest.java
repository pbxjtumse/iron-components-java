package com.xjtu.iron.foundation.id.snowflake;

import com.xjtu.iron.foundation.id.api.IdGenerationException;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

class SnowflakeLongIdGeneratorTest {

    @Test
    void shouldGenerateIncreasingIdsAndEncodeWorkerId() {
        MutableClock clock = new MutableClock(
                SnowflakeOptions.DEFAULT_EPOCH_MILLIS + 1_000L
        );
        SnowflakeLongIdGenerator generator = new SnowflakeLongIdGenerator(
                SnowflakeOptions.builder(7L).build(),
                clock
        );

        long first = generator.nextLongId();
        long second = generator.nextLongId();

        assertTrue(second > first);
        assertEquals(7L, (first >>> 12) & 0x3FFL);
    }

    @Test
    void shouldFailFastWhenClockMovesBackwards() {
        MutableClock clock = new MutableClock(
                SnowflakeOptions.DEFAULT_EPOCH_MILLIS + 1_000L
        );
        SnowflakeLongIdGenerator generator = new SnowflakeLongIdGenerator(
                SnowflakeOptions.builder(1L).build(),
                clock
        );
        generator.nextLongId();
        clock.setMillis(SnowflakeOptions.DEFAULT_EPOCH_MILLIS + 999L);

        assertThrows(IdGenerationException.class, generator::nextLongId);
    }

    @Test
    void shouldUseLogicalTimeWithinConfiguredRollbackThreshold() {
        MutableClock clock = new MutableClock(
                SnowflakeOptions.DEFAULT_EPOCH_MILLIS + 1_000L
        );
        SnowflakeOptions options = SnowflakeOptions.builder(1L)
                .clockRollbackStrategy(ClockRollbackStrategy.USE_LOGICAL_TIME)
                .maxBackwardMillis(5L)
                .build();
        SnowflakeLongIdGenerator generator = new SnowflakeLongIdGenerator(options, clock);
        long first = generator.nextLongId();
        clock.setMillis(SnowflakeOptions.DEFAULT_EPOCH_MILLIS + 998L);
        long second = generator.nextLongId();

        assertTrue(second > first);
    }

    @Test
    void shouldFailInsteadOfSpinningForeverWhenSequenceIsExhausted() {
        Clock fixedClock = Clock.fixed(
                Instant.ofEpochMilli(SnowflakeOptions.DEFAULT_EPOCH_MILLIS + 1_000L),
                ZoneOffset.UTC
        );
        SnowflakeOptions options = SnowflakeOptions.builder(1L)
                .sequenceWaitTimeout(Duration.ZERO)
                .build();
        SnowflakeLongIdGenerator generator =
                new SnowflakeLongIdGenerator(options, fixedClock);

        for (int index = 0; index < 4_096; index++) {
            generator.nextLongId();
        }

        assertThrows(IdGenerationException.class, generator::nextLongId);
    }

    private static final class MutableClock extends Clock {

        private long millis;

        private MutableClock(long millis) {
            this.millis = millis;
        }

        private void setMillis(long millis) {
            this.millis = millis;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public long millis() {
            return millis;
        }
    }
}
