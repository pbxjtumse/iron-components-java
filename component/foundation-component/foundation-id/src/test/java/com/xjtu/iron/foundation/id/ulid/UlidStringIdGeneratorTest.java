package com.xjtu.iron.foundation.id.ulid;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UlidStringIdGeneratorTest {

    @Test
    void shouldGenerateUniqueLexicographicallyMonotonicUlids() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-03T00:00:00Z"), ZoneOffset.UTC);
        UlidStringIdGenerator generator =
                new UlidStringIdGenerator(clock, new SecureRandom(new byte[]{4, 5, 6}));
        Set<String> ids = new HashSet<>();
        String previous = null;

        for (int index = 0; index < 1_000; index++) {
            String current = generator.nextId();
            assertEquals(26, current.length());
            assertTrue(current.matches("[0-9A-HJKMNP-TV-Z]{26}"));
            assertTrue(ids.add(current));
            if (previous != null) {
                assertTrue(previous.compareTo(current) < 0);
            }
            previous = current;
        }
    }
}
