package com.xjtu.iron.distributed.lock.provider.redis;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedisScriptResultParserTest {
    @Test
    void shouldParseLongFromDifferentRedisReturnTypes() {
        RedisScriptResultParser parser = new RedisScriptResultParser();
        assertEquals(1L, parser.parseLong(1L));
        assertEquals(2L, parser.parseLong(2));
        assertEquals(3L, parser.parseLong("3"));
        assertEquals(4L, parser.parseLong("4".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void shouldParseAcquireResult() {
        RedisScriptResultParser.AcquireResult result = new RedisScriptResultParser().parseAcquire(Arrays.asList(0L, 123L));
        assertEquals(0L, result.getFlag());
        assertEquals(123L, result.getRemainingTtlMillis());
    }
}
