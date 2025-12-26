package com.loopers.domain.ranking

/**
 * 상품 랭킹 조회 결과를 담는 데이터 클래스
 *
 * @property productId 상품 ID
 * @property rank 순위 (1-based)
 * @property score 점수
 */
data class ProductRanking(
    val productId: Long,
    val rank: Int,
    val score: Score,
)
