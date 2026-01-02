package com.loopers.application.ranking

import com.loopers.cache.CacheKey
import com.loopers.domain.ranking.RankingPeriod
import java.time.Duration
import java.time.LocalDate

sealed class RankingCacheKeys(override val ttl: Duration) : CacheKey {
    abstract override val key: String
    abstract override val traceKey: String

    /**
     * WEEKLY/MONTHLY ranking cache
     *
     * Key pattern: ranking-cache:v1:{period}:{baseDate}:{offset}:{limit}
     * TTL: 1 hour
     */
    data class RankingList(
        private val period: RankingPeriod,
        private val baseDate: LocalDate,
        private val offset: Long,
        private val limit: Int,
    ) : RankingCacheKeys(ttl = Duration.ofHours(1)) {
        override val key: String = "ranking-cache:v1:${period.key}:$baseDate:$offset:$limit"
        override val traceKey: String = "ranking-cache"

        fun shouldCache(): Boolean = period in listOf(RankingPeriod.WEEKLY, RankingPeriod.MONTHLY)
    }
}
