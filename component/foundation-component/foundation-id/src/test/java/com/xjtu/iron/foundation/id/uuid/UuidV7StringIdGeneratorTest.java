package com.xjtu.iron.foundation.id.uuid;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UuidV7StringIdGeneratorTest {

    @Test
    void shouldGenerateUniqueMonotonicVersionSevenUuidsInSameMillisecond() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-03T00:00:00Z"), ZoneOffset.UTC);
        UuidV7StringIdGenerator generator =
                new UuidV7StringIdGenerator(clock, new SecureRandom(new byte[]{1, 2, 3}));
        Set<String> ids = new HashSet<>();
        String previous = null;

        for (int index = 0; index < 1_000; index++) {
            String current = generator.nextId();
            UUID uuid = UUID.fromString(current);
            assertEquals(7, uuid.version());
            assertEquals(2, uuid.variant());
            assertTrue(ids.add(current));
            if (previous != null) {
                assertTrue(previous.compareTo(current) < 0);
            }
            previous = current;
        }
    }
}
