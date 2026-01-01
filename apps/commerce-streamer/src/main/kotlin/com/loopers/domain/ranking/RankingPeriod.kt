package com.loopers.domain.ranking

import java.time.Instant
import java.time.temporal.ChronoUnit

enum class RankingPeriod(val key: String) {
    HOURLY("hourly"),
    DAILY("daily"),
    ;

    fun subtractOne(instant: Instant): Instant {
        return when (this) {
            HOURLY -> instant.minus(1, ChronoUnit.HOURS)
            DAILY -> instant.minus(1, ChronoUnit.DAYS)
        }
    }

    companion object {
        fun fromString(value: String?): RankingPeriod {
            return when (value?.lowercase()) {
                "daily" -> DAILY
                else -> HOURLY
            }
        }

        fun fromKey(key: String): RankingPeriod {
            return entries.find { it.key == key }
                ?: throw IllegalArgumentException("Unknown RankingPeriod key: $key")
        }
    }
}
