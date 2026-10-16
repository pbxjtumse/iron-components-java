package com.xjtu.iron.foundation.id.decorator;

import com.xjtu.iron.foundation.id.api.IdGenerationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrefixedStringIdGeneratorTest {

    @Test
    void shouldConcatenatePrefixWithoutInventingSeparator() {
        PrefixedStringIdGenerator generator =
                new PrefixedStringIdGenerator("retry_", () -> "123");

        assertEquals("retry_123", generator.nextId());
    }

    @Test
    void shouldUseExplicitSeparator() {
        PrefixedStringIdGenerator generator =
                new PrefixedStringIdGenerator("retry", "-", () -> "123");

        assertEquals("retry-123", generator.nextId());
    }

    @Test
    void shouldRejectBlankDelegateResult() {
        PrefixedStringIdGenerator generator =
                new PrefixedStringIdGenerator("retry", () -> " ");

        assertThrows(IdGenerationException.class, generator::nextId);
    }
}
