package com.loopers.domain.ranking

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object RankingKeyGenerator {

    private const val PREFIX = "ranking:products"

    private val FORMATTER = DateTimeFormatter
        .ofPattern("yyyyMMddHH")
        .withZone(ZoneId.of("Asia/Seoul"))

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
}
