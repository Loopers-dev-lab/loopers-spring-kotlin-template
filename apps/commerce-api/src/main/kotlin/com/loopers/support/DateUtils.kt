package com.loopers.support

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields

object DateUtils {

    fun toYearWeek(date: LocalDate): String {
        val year = date.get(WeekFields.ISO.weekBasedYear())
        val week = date.get(WeekFields.ISO.weekOfWeekBasedYear())
        return String.format("%d-W%02d", year, week)
    }

    fun toYearMonth(date: LocalDate): String {
        return date.format(DateTimeFormatter.ofPattern("yyyy-MM"))
    }

    fun formatDate(date: LocalDate): String {
        return date.format(DateTimeFormatter.BASIC_ISO_DATE)
    }
}
