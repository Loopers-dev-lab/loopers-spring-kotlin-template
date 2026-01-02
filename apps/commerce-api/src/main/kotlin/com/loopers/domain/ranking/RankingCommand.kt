package com.loopers.domain.ranking

import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime

class RankingCommand {
    data class FindRankings(
        val period: RankingPeriod,
        val date: String?,
        val page: Int,
        val size: Int,
    ) {
        init {
            require(page >= 0) { "page must be >= 0: $page" }
            require(size in 1..100) { "size must be between 1 and 100: $size" }
        }
    }
}

private val SEOUL_ZONE = ZoneId.of("Asia/Seoul")

/**
 * Converts FindRankings command to RankingQuery.
 * Parses date string to ZonedDateTime based on period format and converts to Instant.
 * If date is null, uses clock.instant() for current time.
 */
fun RankingCommand.FindRankings.toQuery(clock: Clock): RankingQuery {
    val dateTime = if (date != null) {
        parseDateTime(period, date)
    } else {
        clock.instant()
    }

    return RankingQuery(
        period = period,
        dateTime = dateTime,
        offset = (page * size).toLong(),
        limit = size.toLong(),
    )
}

private fun parseDateTime(period: RankingPeriod, date: String): java.time.Instant {
    return when (period) {
        RankingPeriod.HOURLY -> {
            require(date.length == 10) { "Invalid hourly date format: $date (expected yyyyMMddHH)" }
            val year = date.substring(0, 4).toInt()
            val month = date.substring(4, 6).toInt()
            val day = date.substring(6, 8).toInt()
            val hour = date.substring(8, 10).toInt()
            ZonedDateTime.of(year, month, day, hour, 0, 0, 0, SEOUL_ZONE).toInstant()
        }
        RankingPeriod.DAILY -> {
            require(date.length == 8) { "Invalid daily date format: $date (expected yyyyMMdd)" }
            val year = date.substring(0, 4).toInt()
            val month = date.substring(4, 6).toInt()
            val day = date.substring(6, 8).toInt()
            ZonedDateTime.of(year, month, day, 0, 0, 0, 0, SEOUL_ZONE).toInstant()
        }
        RankingPeriod.WEEKLY, RankingPeriod.MONTHLY -> {
            require(date.length == 8) { "Invalid ${period.name.lowercase()} date format: $date (expected yyyyMMdd)" }
            val year = date.substring(0, 4).toInt()
            val month = date.substring(4, 6).toInt()
            val day = date.substring(6, 8).toInt()
            ZonedDateTime.of(year, month, day, 0, 0, 0, 0, SEOUL_ZONE).toInstant()
        }
    }
}
