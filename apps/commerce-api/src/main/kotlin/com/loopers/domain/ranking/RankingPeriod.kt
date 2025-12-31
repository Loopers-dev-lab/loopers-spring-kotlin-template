package com.loopers.domain.ranking

enum class RankingPeriod {
    HOURLY,
    DAILY,
    ;

    companion object {
        fun fromString(value: String?): RankingPeriod {
            return when (value?.lowercase()) {
                "daily" -> DAILY
                else -> HOURLY
            }
        }
    }
}
