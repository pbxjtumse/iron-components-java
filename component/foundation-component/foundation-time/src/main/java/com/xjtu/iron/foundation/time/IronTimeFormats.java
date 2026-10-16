package com.xjtu.iron.foundation.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 时间格式工具统一门面。
 */
public final class IronTimeFormats {

    private IronTimeFormats() {}

    public static String formatIsoInstant(Instant instant) {
        return instant == null ? null : DateTimeFormatter.ISO_INSTANT.format(instant);
    }

    public static Instant parseIsoInstant(String value) {
        return value == null ? null : Instant.parse(value);
    }

    public static String formatIsoDate(LocalDate date) {
        return date == null ? null : DateTimeFormatter.ISO_LOCAL_DATE.format(date);
    }
}
