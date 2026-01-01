package com.loopers.domain.ranking

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
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
        }
    }

    companion object {
        private val SEOUL_ZONE = ZoneId.of("Asia/Seoul")

        private const val HOURLY_PREFIX = "ranking:products:hourly"
        private const val DAILY_PREFIX = "ranking:products:daily"

        private val HOURLY_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMddHH")
            .withZone(SEOUL_ZONE)

        private val DAILY_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMdd")
            .withZone(SEOUL_ZONE)
    }
}
