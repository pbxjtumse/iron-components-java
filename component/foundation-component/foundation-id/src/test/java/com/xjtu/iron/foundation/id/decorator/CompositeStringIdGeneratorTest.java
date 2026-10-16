package com.xjtu.iron.foundation.id.decorator;

import com.xjtu.iron.foundation.id.api.IdGenerationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompositeStringIdGeneratorTest {

    @Test
    void shouldJoinTechnicalIdSegmentsInDeclaredOrder() {
        CompositeStringIdGenerator generator = new CompositeStringIdGenerator(
                List.of(() -> "cn", () -> "node-1", () -> "0001"),
                ":"
        );

        assertEquals("cn:node-1:0001", generator.nextId());
    }

    @Test
    void shouldRejectBlankGeneratedSegment() {
        CompositeStringIdGenerator generator = new CompositeStringIdGenerator(
                List.of(() -> "cn", () -> ""),
                ":"
        );

        assertThrows(IdGenerationException.class, generator::nextId);
    }
}
