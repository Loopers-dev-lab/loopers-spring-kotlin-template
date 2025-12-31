package com.loopers.domain.ranking

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object RankingKeyGenerator {

    private const val PREFIX = "ranking:products"
    private const val DAILY_PREFIX = "ranking:products:daily"
    private val SEOUL_ZONE = ZoneId.of("Asia/Seoul")

    private val FORMATTER = DateTimeFormatter
        .ofPattern("yyyyMMddHH")
        .withZone(SEOUL_ZONE)

    private val DAILY_FORMATTER = DateTimeFormatter
        .ofPattern("yyyyMMdd")
        .withZone(SEOUL_ZONE)

    /**
     * Generates bucket key from instant
     * @param instant Time (on the hour)
     * @return Format: "ranking:products:yyyyMMddHH"
     */
    fun bucketKey(instant: Instant): String {
        val truncated = instant.truncatedTo(ChronoUnit.HOURS)
        return "$PREFIX:${FORMATTER.format(truncated)}"
    }

    /**
     * Current bucket key
     */
    fun currentBucketKey(): String = bucketKey(Instant.now())

    /**
     * Previous bucket key from given instant (1 hour before)
     * @param instant Time reference
     * @return Format: "ranking:products:yyyyMMddHH" for previous hour
     */
    fun previousBucketKey(instant: Instant): String {
        val truncated = instant.truncatedTo(ChronoUnit.HOURS)
        val previousHour = truncated.minus(1, ChronoUnit.HOURS)
        return "$PREFIX:${FORMATTER.format(previousHour)}"
    }

    /**
     * Previous bucket key based on current time
     * @return Format: "ranking:products:yyyyMMddHH" for previous hour
     */
    fun previousBucketKey(): String = previousBucketKey(Instant.now())

    /**
     * Next bucket key from given instant (1 hour after)
     * @param instant Time reference
     * @return Format: "ranking:products:yyyyMMddHH" for next hour
     */
    fun nextBucketKey(instant: Instant): String {
        val truncated = instant.truncatedTo(ChronoUnit.HOURS)
        val nextHour = truncated.plus(1, ChronoUnit.HOURS)
        return "$PREFIX:${FORMATTER.format(nextHour)}"
    }

    /**
     * Next bucket key based on current time
     * @return Format: "ranking:products:yyyyMMddHH" for next hour
     */
    fun nextBucketKey(): String = nextBucketKey(Instant.now())

    /**
     * Generates daily bucket key from LocalDate
     * @param date Date for the bucket
     * @return Format: "ranking:products:daily:yyyyMMdd"
     */
    fun dailyBucketKey(date: LocalDate): String {
        return "$DAILY_PREFIX:${DAILY_FORMATTER.format(date.atStartOfDay(SEOUL_ZONE))}"
    }

    /**
     * Current daily bucket key based on current date
     * @return Format: "ranking:products:daily:yyyyMMdd" for today
     */
    fun currentDailyBucketKey(): String = dailyBucketKey(LocalDate.now(SEOUL_ZONE))
}
