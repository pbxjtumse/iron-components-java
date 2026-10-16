package com.xjtu.iron.foundation.id.nanoid;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NanoIdStringIdGeneratorTest {

    @Test
    void shouldGenerateIdsUsingConfiguredAlphabetAndSize() {
        NanoIdOptions options = NanoIdOptions.builder()
                .alphabet("abc123")
                .size(16)
                .build();
        NanoIdStringIdGenerator generator =
                new NanoIdStringIdGenerator(options, new SecureRandom(new byte[]{7, 8, 9}));
        Set<String> ids = new HashSet<>();

        for (int index = 0; index < 1_000; index++) {
            String id = generator.nextId();
            assertEquals(16, id.length());
            assertTrue(id.matches("[abc123]{16}"));
            assertTrue(ids.add(id));
        }
    }

    @Test
    void shouldSupportSingleCharacterAlphabet() {
        NanoIdOptions options = NanoIdOptions.builder()
                .alphabet("x")
                .size(5)
                .build();

        assertEquals("xxxxx", new NanoIdStringIdGenerator(options).nextId());
    }

    @Test
    void shouldRejectDuplicateAlphabetCharacters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> NanoIdOptions.builder().alphabet("aabc").build()
        );
    }
}
