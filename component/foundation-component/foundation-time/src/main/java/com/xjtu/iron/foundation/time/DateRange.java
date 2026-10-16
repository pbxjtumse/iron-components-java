package com.xjtu.iron.foundation.time;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 日期闭区间值对象，开始和结束日期均包含。
 */
public final class DateRange {

    private final LocalDate startInclusive;
    private final LocalDate endInclusive;

    public DateRange(LocalDate startInclusive, LocalDate endInclusive) {
        this.startInclusive = Objects.requireNonNull(startInclusive, "startInclusive must not be null");
        this.endInclusive = Objects.requireNonNull(endInclusive, "endInclusive must not be null");
        if (endInclusive.isBefore(startInclusive)) {
            throw new IllegalArgumentException("endInclusive must not be before startInclusive");
        }
    }

    public LocalDate getStartInclusive() { return startInclusive; }

    public LocalDate getEndInclusive() { return endInclusive; }

    public boolean contains(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        return !date.isBefore(startInclusive) && !date.isAfter(endInclusive);
    }

    public boolean overlaps(DateRange other) {
        Objects.requireNonNull(other, "other must not be null");
        return !endInclusive.isBefore(other.startInclusive) && !other.endInclusive.isBefore(startInclusive);
    }
}
