package com.xjtu.iron.foundation.time;

import java.time.LocalTime;
import java.util.Objects;

/**
 * 本地时间半开区间值对象，开始包含，结束不包含。
 */
public final class TimeRange {

    private final LocalTime startInclusive;
    private final LocalTime endExclusive;

    public TimeRange(LocalTime startInclusive, LocalTime endExclusive) {
        this.startInclusive = Objects.requireNonNull(startInclusive, "startInclusive must not be null");
        this.endExclusive = Objects.requireNonNull(endExclusive, "endExclusive must not be null");
        if (!endExclusive.isAfter(startInclusive)) {
            throw new IllegalArgumentException("endExclusive must be after startInclusive");
        }
    }

    public LocalTime getStartInclusive() { return startInclusive; }

    public LocalTime getEndExclusive() { return endExclusive; }

    public boolean contains(LocalTime time) {
        Objects.requireNonNull(time, "time must not be null");
        return !time.isBefore(startInclusive) && time.isBefore(endExclusive);
    }
}
