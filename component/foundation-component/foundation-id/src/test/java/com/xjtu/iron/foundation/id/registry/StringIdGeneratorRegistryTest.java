package com.xjtu.iron.foundation.id.registry;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StringIdGeneratorRegistryTest {

    @Test
    void shouldResolveGeneratorByExactName() {
        StringIdGeneratorRegistry registry = new StringIdGeneratorRegistry(
                Map.of("retry", () -> "retry-1")
        );

        assertEquals("retry-1", registry.require("retry").nextId());
        assertTrue(registry.contains("retry"));
        assertTrue(registry.find("retry").isPresent());
        assertFalse(registry.find("message").isPresent());
    }

    @Test
    void shouldFailWhenRequiredNameDoesNotExist() {
        StringIdGeneratorRegistry registry = new StringIdGeneratorRegistry(
                Map.of("retry", () -> "retry-1")
        );

        assertThrows(IllegalArgumentException.class, () -> registry.require("message"));
    }

    @Test
    void shouldRejectDuplicateBuilderRegistration() {
        StringIdGeneratorRegistry.Builder builder = StringIdGeneratorRegistry.builder()
                .register("retry", () -> "one");

        assertThrows(
                IllegalArgumentException.class,
                () -> builder.register("retry", () -> "two")
        );
    }

    @Test
    void shouldExposeImmutableRegistryView() {
        StringIdGeneratorRegistry registry = StringIdGeneratorRegistry.builder()
                .register("retry", () -> "one")
                .build();

        assertThrows(
                UnsupportedOperationException.class,
                () -> registry.asMap().put("message", () -> "two")
        );
    }

    @Test
    void shouldRejectNamesWithSurroundingWhitespace() {
        assertThrows(
                IllegalArgumentException.class,
                () -> StringIdGeneratorRegistry.builder()
                        .register(" retry ", () -> "one")
        );
    }
}
