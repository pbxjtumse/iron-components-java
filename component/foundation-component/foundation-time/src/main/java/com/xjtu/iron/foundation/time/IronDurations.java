package com.xjtu.iron.foundation.time;

import java.time.Duration;
import java.util.Locale;

/**
 * Duration 工具统一门面。
 */
public final class IronDurations {

    private IronDurations() {}

    public static Duration parseSimple(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("duration must not be blank");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(normalized.substring(0, normalized.length() - 2)));
        }
        if (normalized.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        if (normalized.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        if (normalized.endsWith("h")) {
            return Duration.ofHours(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
        }
        if (normalized.startsWith("pt")) {
            return Duration.parse(normalized.toUpperCase(Locale.ROOT));
        }
        throw new IllegalArgumentException("unsupported duration format: " + value);
    }

    public static long toMillisCeil(Duration duration) {
        if (duration == null) {
            return 0L;
        }
        long millis = duration.toMillis();
        return duration.minusMillis(millis).isZero() ? millis : millis + 1;
    }
}
