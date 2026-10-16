package com.xjtu.iron.foundation.test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * 可推进测试时钟，避免测试代码使用 Thread.sleep。
 */
public final class MutableClock extends Clock {

    private Instant instant;
    private final ZoneId zone;

    public MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    public void advance(Duration duration) { this.instant = this.instant.plus(duration); }

    @Override public ZoneId getZone() { return zone; }

    @Override public Clock withZone(ZoneId zone) { return new MutableClock(instant, zone); }

    @Override public Instant instant() { return instant; }
}
