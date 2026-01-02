package com.loopers.domain.ranking

import java.time.Instant
import java.time.temporal.ChronoUnit

enum class RankingPeriod(val key: String) {
    HOURLY("hourly"),
    DAILY("daily"),
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    ;

    fun subtractOne(instant: Instant): Instant {
        return when (this) {
            HOURLY -> instant.minus(1, ChronoUnit.HOURS)
            DAILY -> instant.minus(1, ChronoUnit.DAYS)
            WEEKLY -> instant.minus(7, ChronoUnit.DAYS)
            MONTHLY -> instant.minus(30, ChronoUnit.DAYS)
        }
    }

    companion object {
        fun fromString(value: String?): RankingPeriod {
            return when (value?.lowercase()) {
                "daily" -> DAILY
                "weekly" -> WEEKLY
                "monthly" -> MONTHLY
                else -> HOURLY
            }
        }

        fun fromKey(key: String): RankingPeriod {
            return entries.find { it.key == key }
                ?: throw IllegalArgumentException("Unknown RankingPeriod key: $key")
        }
    }
}
