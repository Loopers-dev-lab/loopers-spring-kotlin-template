package com.loopers.application.ranking.dto

object RankingResult {
    data class RankedInfo(
        val productId: Long,
        val name: String,
        val price: Long,
        val brandName: String,
        val score: Double,
        val rank: Long,
    )

    data class RankInfo(
        val score: Double,
        val rank: Long,
    )
}
