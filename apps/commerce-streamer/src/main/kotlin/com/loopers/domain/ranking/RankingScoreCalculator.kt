package com.loopers.domain.ranking

import kotlin.math.ln
import kotlin.math.max

/**
 * commerce-streamer용 랭킹 점수 확장
 *
 * 가중치 합산(Weighted Sum) 방식으로 점수 계산:
 * Score(p) = W(view) * Count(view) + W(like) * Count(like) + W(order) * Score(order)
 *
 * 유연한 가중치 조정 가능
 */
object RankingScoreCalculator {
    /**
     * 조회 이벤트 점수
     *
     * @param weight 가중치 (기본값: 0.1)
     */
    fun fromView(weight: Double = 0.1): RankingScore {
        validateWeight(weight)
        return RankingScore(weight)
    }

    /**
     * 좋아요 이벤트 점수
     *
     * @param weight 가중치 (기본값: 0.2)
     */
    fun fromLike(weight: Double = 0.2): RankingScore {
        validateWeight(weight)
        return RankingScore(weight)
    }

    /**
     * 주문 이벤트 점수
     *
     * 주문 금액이 클수록 점수가 높아지지만, 로그 스케일로 정규화하여
     * 지나치게 높은 금액이 점수를 독식하는 것을 방지
     *
     * @param priceAtOrder 주문 당시 가격
     * @param quantity 수량
     * @param weight 가중치 (기본값: 0.7)
     */
    fun fromOrder(priceAtOrder: Long, quantity: Int, weight: Double = 0.7): RankingScore {
        validateWeight(weight)

        // Long 곱셈 오버플로우 방지를 위해 Double로 변환 후 계산
        val totalAmount = priceAtOrder.toDouble() * quantity

        // 0 이하의 totalAmount는 안전하게 처리
        if (totalAmount <= 0) {
            return RankingScore(0.0)
        }

        // 최소값을 1로 보장하여 ln(0) 방지
        val safeAmount = max(1.0, totalAmount)
        // 로그 스케일로 정규화 (1 + ln(x))
        val normalizedScore = 1.0 + ln(safeAmount)
        return RankingScore(weight * normalizedScore)
    }

    private fun validateWeight(weight: Double) {
        require(weight >= 0.0) { "가중치는 0 이상이어야 합니다: weight=$weight" }
    }
}
