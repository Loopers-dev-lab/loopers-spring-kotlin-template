package com.loopers.domain.ranking

class RankingCommand {
    data class FindRankings(
        val period: RankingPeriod,
        val date: String?,
        val page: Int,
        val size: Int,
    ) {
        init {
            require(page >= 0) { "page must be >= 0: $page" }
            require(size in 1..100) { "size must be between 1 and 100: $size" }
        }
    }
}
