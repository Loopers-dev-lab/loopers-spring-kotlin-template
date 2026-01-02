package com.loopers.application.ranking

import com.loopers.domain.ranking.ProductRanking
import java.math.BigDecimal

/**
 * Versioned DTO for cached rankings
 *
 * Create new class (CachedRankingV2) when schema changes
 * Related cache key: RankingCacheKeys.RankingList
 */
data class CachedRankingV1(
    val rankings: List<Entry>,
) {
    data class Entry(
        val productId: Long,
        val rank: Int,
        val score: BigDecimal,
    )

    fun toProductRankings(): List<ProductRanking> = rankings.map {
        ProductRanking(
            productId = it.productId,
            rank = it.rank,
            score = it.score,
        )
    }

    companion object {
        fun from(rankings: List<ProductRanking>): CachedRankingV1 = CachedRankingV1(
            rankings = rankings.map { Entry(it.productId, it.rank, it.score) },
        )
    }
}
