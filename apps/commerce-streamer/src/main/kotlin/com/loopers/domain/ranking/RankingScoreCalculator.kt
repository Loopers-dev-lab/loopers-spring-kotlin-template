package com.loopers.domain.ranking

/**
 * 랭킹 점수 계산 유틸리티
 *
 * 이벤트 타입별 가중치를 적용하여 랭킹 점수를 계산합니다.
 *
 * 점수 계산 공식:
 * - 조회수: 0.1 × 1 = 0.1
 * - 좋아요: 0.2 × 1 = 0.2
 * - 주문: 0.6 × log10(price × quantity)
 */
object RankingScoreCalculator {

    private const val VIEW_WEIGHT = 0.1
    private const val LIKE_WEIGHT = 0.2
    private const val ORDER_WEIGHT = 0.6

    /**
     * 조회수 점수 계산
     * @return 0.1
     */
    fun calculateViewScore(): Double {
        return VIEW_WEIGHT * 1.0
    }

    /**
     * 좋아요 점수 계산
     * @return 0.2
     */
    fun calculateLikeScore(): Double {
        return LIKE_WEIGHT * 1.0
    }

    /**
     * 주문 점수 계산 (log10 정규화)
     * @param price 상품 가격
     * @param quantity 주문 수량
     * @return 0.6 × log10(price × quantity)
     */
    fun calculateOrderScore(price: Long, quantity: Int): Double {
        val amount = (price * quantity).toDouble()
        return if (amount > 0) {
            ORDER_WEIGHT * kotlin.math.log10(amount)
        } else {
            0.0
        }
    }
}
