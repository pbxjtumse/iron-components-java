package com.xjtu.iron.foundation.time;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Instant 工具统一门面。
 */
public final class IronInstants {

    private IronInstants() {}

    public static Instant truncateToMillis(Instant instant) {
        return instant == null ? null : instant.truncatedTo(ChronoUnit.MILLIS);
    }

    public static boolean isBeforeOrEqual(Instant left, Instant right) {
        return left != null && right != null && !left.isAfter(right);
    }
}
