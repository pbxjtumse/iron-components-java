package com.xjtu.iron.distributed.lock.provider.redis;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/** Redis Lua 脚本返回值解析器。 */
public final class RedisScriptResultParser {

    public AcquireResult parseAcquire(Object raw) {
        List<?> values = asList(raw);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("redis acquire script result is empty");
        }
        long flag = asLong(values.get(0));
        Object second = values.size() > 1 ? values.get(1) : null;
        return new AcquireResult(flag, second);
    }

    public long parseLong(Object raw) {
        return asLong(raw);
    }

    private static List<?> asList(Object raw) {
        if (raw instanceof List) {
            return (List<?>) raw;
        }
        if (raw instanceof Object[]) {
            return Arrays.asList((Object[]) raw);
        }
        throw new IllegalArgumentException("redis script result is not a list: " + raw);
    }

    private static long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof byte[]) {
            return Long.parseLong(new String((byte[]) value, StandardCharsets.UTF_8));
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return 0L;
        }
        return Long.parseLong(text);
    }

    public static final class AcquireResult {
        private final long flag;
        private final Object value;

        private AcquireResult(long flag, Object value) {
            this.flag = flag;
            this.value = value;
        }

        public long getFlag() { return flag; }
        public long getRemainingTtlMillis() { return asLong(value); }
        public Long getFencingToken() {
            if (value == null) { return null; }
            long parsed = asLong(value);
            return parsed <= 0 ? null : parsed;
        }
    }
}
