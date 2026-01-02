package com.loopers.support

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

object DateUtils {

    /**
     * ISO Week 형식: "2025-W52"
     */
    fun toYearWeek(date: LocalDate): String {
        val year = date.get(WeekFields.ISO.weekBasedYear())
        val week = date.get(WeekFields.ISO.weekOfWeekBasedYear())
        return String.format("%d-W%02d", year, week)
    }

    /**
     * Year-Month 형식: "2025-12"
     */
    fun toYearMonth(date: LocalDate): String {
        return date.format(DateTimeFormatter.ofPattern("yyyy-MM"))
    }

    /**
     * ISO Week의 월요일~일요일 날짜 리스트
     *
     * 예: "2025-W52" → [2025-12-22(월), ..., 2025-12-28(일)]
     *
     * ISO 8601 표준: 1월 4일은 항상 해당 연도의 W01에 속함
     * Edge case 처리: 2023-01-01은 ISO 2022-W52에 속하므로 anchor date 사용
     */
    fun getWeekDates(yearWeek: String): List<LocalDate> {
        val (year, week) = yearWeek.split("-W").let {
            it[0].toInt() to it[1].toInt()
        }

        // Jan 4 is always in week 1 of the week-based year (ISO 8601)
        val anchorDate = LocalDate.of(year, 1, 4)
        val monday = anchorDate
            .with(WeekFields.ISO.weekBasedYear(), year.toLong())
            .with(WeekFields.ISO.weekOfWeekBasedYear(), week.toLong())
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        return (0..6).map { monday.plusDays(it.toLong()) }
    }

    /**
     * Year-Month의 모든 날짜 리스트
     *
     * 예: "2025-12" → [2025-12-01, ..., 2025-12-31]
     */
    fun getMonthDates(yearMonth: String): List<LocalDate> {
        val (year, month) = yearMonth.split("-").let {
            it[0].toInt() to it[1].toInt()
        }

        val firstDay = LocalDate.of(year, month, 1)
        val lastDay = firstDay.with(TemporalAdjusters.lastDayOfMonth())

        return (0 until lastDay.dayOfMonth).map { firstDay.plusDays(it.toLong()) }
    }

    /**
     * yyyyMMdd → LocalDate
     */
    fun parseDate(dateStr: String): LocalDate {
        return LocalDate.parse(dateStr, DateTimeFormatter.BASIC_ISO_DATE)
    }

    /**
     * LocalDate → yyyyMMdd
     */
    fun formatDate(date: LocalDate): String {
        return date.format(DateTimeFormatter.BASIC_ISO_DATE)
    }
}
