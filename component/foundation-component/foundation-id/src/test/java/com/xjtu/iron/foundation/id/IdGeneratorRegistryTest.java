package com.xjtu.iron.foundation.id;

import com.xjtu.iron.foundation.id.registry.StringIdGeneratorRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdGeneratorRegistryTest {

    @Test
    void shouldResolveNamedGenerator() {
        StringIdGeneratorRegistry registry = new StringIdGeneratorRegistry(Map.of("message", () -> "message-1"));
        assertEquals("message-1", registry.require("message").nextId());
        assertThrows(IllegalArgumentException.class, () -> registry.require("unknown"));
    }
}
