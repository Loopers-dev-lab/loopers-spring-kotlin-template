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
     * Previous hour's bucket key (for fallback)
     */
    fun previousBucketKey(): String {
        val previousHour = Instant.now().minus(1, ChronoUnit.HOURS)
        return bucketKey(previousHour)
    }

    /**
     * Generate daily bucket key
     * @param date Date
     * @return Format: "ranking:products:daily:yyyyMMdd"
     */
    fun dailyBucketKey(date: LocalDate): String {
        val instant = date.atStartOfDay(SEOUL_ZONE).toInstant()
        return "$DAILY_PREFIX:${DAILY_FORMATTER.format(instant)}"
    }

    /**
     * Current daily bucket key (today)
     */
    fun currentDailyBucketKey(): String = dailyBucketKey(LocalDate.now(SEOUL_ZONE))

    /**
     * Previous daily bucket key (yesterday)
     */
    fun previousDailyBucketKey(): String = dailyBucketKey(LocalDate.now(SEOUL_ZONE).minusDays(1))
}
