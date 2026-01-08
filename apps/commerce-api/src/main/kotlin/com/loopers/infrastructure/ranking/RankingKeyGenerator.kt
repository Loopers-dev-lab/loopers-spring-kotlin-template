package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.RankingPeriod
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Component
class RankingKeyGenerator(
    private val clock: Clock,
) {

    /**
     * Generates bucket key from Instant and period
     * - Hourly: ranking:products:hourly:yyyyMMddHH
     * - Daily: ranking:products:daily:yyyyMMdd
     * - Weekly: ranking:products:weekly:yyyyMMdd
     * - Monthly: ranking:products:monthly:yyyyMMdd
     *
     * Internally converts to Seoul timezone for key formatting (spec#8.2 - Redis keys remain KST-based)
     */
    fun bucketKey(period: RankingPeriod, instant: Instant): String {
        val seoulDateTime = instant.atZone(SEOUL_ZONE)
        return when (period) {
            RankingPeriod.HOURLY -> {
                val truncated = seoulDateTime.truncatedTo(ChronoUnit.HOURS)
                "$HOURLY_PREFIX:${HOURLY_FORMATTER.format(truncated)}"
            }

            RankingPeriod.DAILY -> {
                "$DAILY_PREFIX:${DAILY_FORMATTER.format(seoulDateTime)}"
            }

            RankingPeriod.WEEKLY -> {
                "$WEEKLY_PREFIX:${DAILY_FORMATTER.format(seoulDateTime)}"
            }

            RankingPeriod.MONTHLY -> {
                "$MONTHLY_PREFIX:${DAILY_FORMATTER.format(seoulDateTime)}"
            }
        }
    }

    /**
     * Generates bucket key from date string and period
     * - Hourly: ranking:products:hourly:yyyyMMddHH (expects 10-digit date string)
     * - Daily: ranking:products:daily:yyyyMMdd (expects 8-digit date string)
     * - Weekly: ranking:products:weekly:yyyyMMdd (expects 8-digit date string)
     * - Monthly: ranking:products:monthly:yyyyMMdd (expects 8-digit date string)
     */
    fun bucketKey(period: RankingPeriod, date: String): String {
        return when (period) {
            RankingPeriod.HOURLY -> "$HOURLY_PREFIX:$date"
            RankingPeriod.DAILY -> "$DAILY_PREFIX:$date"
            RankingPeriod.WEEKLY -> "$WEEKLY_PREFIX:$date"
            RankingPeriod.MONTHLY -> "$MONTHLY_PREFIX:$date"
        }
    }

    /**
     * Current bucket key for the given period
     */
    fun currentBucketKey(period: RankingPeriod): String {
        return bucketKey(period, clock.instant())
    }

    /**
     * Previous bucket key by parsing the given bucket key and subtracting one period
     * @param bucketKey Format: ranking:products:hourly:yyyyMMddHH or ranking:products:daily:yyyyMMdd
     * @return Previous period's bucket key
     */
    fun previousBucketKey(bucketKey: String): String {
        val parts = bucketKey.split(":")
        require(parts.size == 4) { "Invalid bucket key format: $bucketKey" }
        val periodKey = parts[2] // "hourly" or "daily"
        val date = parts[3] // date portion
        val period = RankingPeriod.Companion.fromKey(periodKey)
        val dateTime = parseDateTime(period, date)
        val previousInstant = period.subtractOne(dateTime.toInstant())
        return bucketKey(period, previousInstant)
    }

    private fun parseDateTime(period: RankingPeriod, date: String): ZonedDateTime {
        return when (period) {
            RankingPeriod.HOURLY -> {
                // Parse yyyyMMddHH format
                require(date.length == 10) { "Invalid hourly date format: $date (expected yyyyMMddHH)" }
                val year = date.substring(0, 4).toInt()
                val month = date.substring(4, 6).toInt()
                val day = date.substring(6, 8).toInt()
                val hour = date.substring(8, 10).toInt()
                ZonedDateTime.of(year, month, day, hour, 0, 0, 0, SEOUL_ZONE)
            }

            RankingPeriod.DAILY, RankingPeriod.WEEKLY, RankingPeriod.MONTHLY -> {
                // Parse yyyyMMdd format (same format for daily, weekly, and monthly)
                require(date.length == 8) { "Invalid ${period.key} date format: $date (expected yyyyMMdd)" }
                val year = date.substring(0, 4).toInt()
                val month = date.substring(4, 6).toInt()
                val day = date.substring(6, 8).toInt()
                ZonedDateTime.of(year, month, day, 0, 0, 0, 0, SEOUL_ZONE)
            }
        }
    }

    companion object {
        private val SEOUL_ZONE = ZoneId.of("Asia/Seoul")

        private const val HOURLY_PREFIX = "ranking:products:hourly"
        private const val DAILY_PREFIX = "ranking:products:daily"
        private const val WEEKLY_PREFIX = "ranking:products:weekly"
        private const val MONTHLY_PREFIX = "ranking:products:monthly"

        private val HOURLY_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMddHH")
            .withZone(SEOUL_ZONE)

        private val DAILY_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMdd")
            .withZone(SEOUL_ZONE)
    }
}
