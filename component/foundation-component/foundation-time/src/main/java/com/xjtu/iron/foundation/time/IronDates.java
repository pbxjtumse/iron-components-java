package com.xjtu.iron.foundation.time;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 日期工具统一门面，不包含业务日历和节假日规则。
 */
public final class IronDates {

    private IronDates() {}

    public static LocalDate firstDayOfMonth(LocalDate date) {
        return YearMonth.from(date).atDay(1);
    }

    public static LocalDate lastDayOfMonth(LocalDate date) {
        return YearMonth.from(date).atEndOfMonth();
    }

    public static List<LocalDate> days(DateRange range) {
        if (range == null) {
            return Collections.emptyList();
        }
        List<LocalDate> result = new ArrayList<>();
        LocalDate current = range.getStartInclusive();
        while (!current.isAfter(range.getEndInclusive())) {
            result.add(current);
            current = current.plusDays(1);
        }
        return Collections.unmodifiableList(result);
    }
}
