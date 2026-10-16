package com.xjtu.iron.foundation.id.factory;

import com.xjtu.iron.foundation.id.api.LongIdGenerator;
import com.xjtu.iron.foundation.id.api.StringIdGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdGeneratorsTest {

    @Test
    void shouldCreateAllPhaseOneGeneratorFamilies() {
        UUID uuidV4 = UUID.fromString(IdGenerators.uuidV4().nextId());
        UUID uuidV7 = UUID.fromString(IdGenerators.uuidV7().nextId());
        String ulid = IdGenerators.ulid().nextId();
        String nanoId = IdGenerators.nanoId().nextId();
        LongIdGenerator snowflake = IdGenerators.snowflake(1L);

        assertEquals(4, uuidV4.version());
        assertEquals(7, uuidV7.version());
        assertEquals(26, ulid.length());
        assertEquals(21, nanoId.length());
        assertTrue(snowflake.nextLongId() > 0L);
    }

    @Test
    void shouldCreateDecoratedGenerators() {
        StringIdGenerator prefixed = IdGenerators.prefixed("retry_", () -> "1");
        StringIdGenerator composite = IdGenerators.composite(
                List.of(() -> "cn", () -> "1"),
                "-"
        );

        assertEquals("retry_1", prefixed.nextId());
        assertEquals("cn-1", composite.nextId());
    }
}
