package com.loopers.domain.ranking

import java.time.ZonedDateTime

enum class RankingPeriod(val key: String) {
    HOURLY("hourly"),
    DAILY("daily"),
    ;

    fun subtractOne(dateTime: ZonedDateTime): ZonedDateTime {
        return when (this) {
            HOURLY -> dateTime.minusHours(1)
            DAILY -> dateTime.minusDays(1)
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
