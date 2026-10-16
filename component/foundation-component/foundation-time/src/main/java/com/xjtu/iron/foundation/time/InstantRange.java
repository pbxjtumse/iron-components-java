package com.xjtu.iron.foundation.time;

import java.time.Instant;
import java.util.Objects;

/**
 * Instant 半开区间值对象，开始包含，结束不包含。
 */
public final class InstantRange {

    private final Instant startInclusive;
    private final Instant endExclusive;

    public InstantRange(Instant startInclusive, Instant endExclusive) {
        this.startInclusive = Objects.requireNonNull(startInclusive, "startInclusive must not be null");
        this.endExclusive = Objects.requireNonNull(endExclusive, "endExclusive must not be null");
        if (!endExclusive.isAfter(startInclusive)) {
            throw new IllegalArgumentException("endExclusive must be after startInclusive");
        }
    }

    public Instant getStartInclusive() { return startInclusive; }

    public Instant getEndExclusive() { return endExclusive; }

    public boolean contains(Instant instant) {
        Objects.requireNonNull(instant, "instant must not be null");
        return !instant.isBefore(startInclusive) && instant.isBefore(endExclusive);
    }
}
