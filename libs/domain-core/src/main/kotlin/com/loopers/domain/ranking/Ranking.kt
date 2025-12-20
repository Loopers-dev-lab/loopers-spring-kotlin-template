package com.loopers.domain.ranking

/**
 * 랭킹 정보를 나타내는 도메인 객체
 */
data class Ranking(
    /**
     * 상품 ID
     */
    val productId: Long,

    /**
     * 랭킹 점수
     */
    val score: RankingScore,

    /**
     * 순위 (1부터 시작)
     */
    val rank: Int,
) {
    init {
        require(rank > 0) { "순위는 1 이상이어야 합니다: rank=$rank" }
    }

    companion object {
        /**
         * Redis ZSET 항목으로부터 Ranking 생성
         */
        fun from(productId: Long, score: Double, rank: Int): Ranking {
            return Ranking(
                productId = productId,
                score = RankingScore(score),
                rank = rank,
            )
        }
    }
}
