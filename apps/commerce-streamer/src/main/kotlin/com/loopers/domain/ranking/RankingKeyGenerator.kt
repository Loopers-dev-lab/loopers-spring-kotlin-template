package com.loopers.domain.ranking

import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Component
class RankingKeyGenerator {

    /**
     * Generates bucket key from ZonedDateTime and period
     * - Hourly: ranking:products:hourly:yyyyMMddHH
     * - Daily: ranking:products:daily:yyyyMMdd
     */
    fun bucketKey(period: RankingPeriod, dateTime: ZonedDateTime): String {
        val seoulDateTime = dateTime.withZoneSameInstant(SEOUL_ZONE)
        return when (period) {
            RankingPeriod.HOURLY -> {
                val truncated = seoulDateTime.truncatedTo(ChronoUnit.HOURS)
                "$HOURLY_PREFIX:${HOURLY_FORMATTER.format(truncated)}"
            }
            RankingPeriod.DAILY -> {
                "$DAILY_PREFIX:${DAILY_FORMATTER.format(seoulDateTime)}"
            }
        }
    }

    /**
     * Generates bucket key from date string and period
     * - Hourly: ranking:products:hourly:yyyyMMddHH (expects 10-digit date string)
     * - Daily: ranking:products:daily:yyyyMMdd (expects 8-digit date string)
     */
    fun bucketKey(period: RankingPeriod, date: String): String {
        return when (period) {
            RankingPeriod.HOURLY -> "$HOURLY_PREFIX:$date"
            RankingPeriod.DAILY -> "$DAILY_PREFIX:$date"
        }
    }

    /**
     * Current bucket key for the given period
     */
    fun currentBucketKey(period: RankingPeriod): String {
        val now = ZonedDateTime.now(SEOUL_ZONE)
        return bucketKey(period, now)
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
        val period = RankingPeriod.fromKey(periodKey)
        val dateTime = parseDateTime(period, date)
        val previousDateTime = period.subtractOne(dateTime)
        return bucketKey(period, previousDateTime)
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
            RankingPeriod.DAILY -> {
                // Parse yyyyMMdd format
                require(date.length == 8) { "Invalid daily date format: $date (expected yyyyMMdd)" }
                val year = date.substring(0, 4).toInt()
                val month = date.substring(4, 6).toInt()
                val day = date.substring(6, 8).toInt()
                ZonedDateTime.of(year, month, day, 0, 0, 0, 0, SEOUL_ZONE)
            }
        }
    }

    // ============================================
    // Backward compatibility methods (deprecated)
    // ============================================

    /**
     * @deprecated Use bucketKey(RankingPeriod.HOURLY, dateTime) instead
     */
    fun bucketKey(instant: Instant): String {
        val truncated = instant.truncatedTo(ChronoUnit.HOURS)
        return "$LEGACY_PREFIX:${LEGACY_FORMATTER.format(truncated)}"
    }

    /**
     * @deprecated Use currentBucketKey(RankingPeriod.HOURLY) instead
     */
    fun currentBucketKey(): String = bucketKey(Instant.now())

    /**
     * @deprecated Use previousBucketKey(bucketKey) instead
     */
    fun previousBucketKey(): String {
        val previousHour = Instant.now().minus(1, ChronoUnit.HOURS)
        return bucketKey(previousHour)
    }

    /**
     * Previous bucket key from given instant (1 hour before)
     * @param instant Time reference
     * @return Format: "ranking:products:yyyyMMddHH" for previous hour
     * @deprecated Use previousBucketKey(bucketKey) instead
     */
    fun previousBucketKey(instant: Instant): String {
        val truncated = instant.truncatedTo(ChronoUnit.HOURS)
        val previousHour = truncated.minus(1, ChronoUnit.HOURS)
        return "$LEGACY_PREFIX:${LEGACY_FORMATTER.format(previousHour)}"
    }

    /**
     * Next bucket key from given instant (1 hour after)
     * @param instant Time reference
     * @return Format: "ranking:products:yyyyMMddHH" for next hour
     */
    fun nextBucketKey(instant: Instant): String {
        val truncated = instant.truncatedTo(ChronoUnit.HOURS)
        val nextHour = truncated.plus(1, ChronoUnit.HOURS)
        return "$LEGACY_PREFIX:${LEGACY_FORMATTER.format(nextHour)}"
    }

    /**
     * Next bucket key based on current time
     * @return Format: "ranking:products:yyyyMMddHH" for next hour
     */
    fun nextBucketKey(): String = nextBucketKey(Instant.now())

    /**
     * @deprecated Use bucketKey(RankingPeriod.DAILY, dateTime) instead
     */
    fun dailyBucketKey(date: LocalDate): String {
        val instant = date.atStartOfDay(SEOUL_ZONE).toInstant()
        return "$DAILY_PREFIX:${DAILY_FORMATTER.format(instant)}"
    }

    /**
     * @deprecated Use currentBucketKey(RankingPeriod.DAILY) instead
     */
    fun currentDailyBucketKey(): String = dailyBucketKey(LocalDate.now(SEOUL_ZONE))

    companion object {
        private val SEOUL_ZONE = ZoneId.of("Asia/Seoul")

        private const val HOURLY_PREFIX = "ranking:products:hourly"
        private const val DAILY_PREFIX = "ranking:products:daily"

        // Legacy prefix without period indicator (for backward compatibility)
        private const val LEGACY_PREFIX = "ranking:products"

        private val HOURLY_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMddHH")
            .withZone(SEOUL_ZONE)

        private val DAILY_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMdd")
            .withZone(SEOUL_ZONE)

        // Legacy formatter (for backward compatibility)
        private val LEGACY_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMddHH")
            .withZone(SEOUL_ZONE)
    }
}
