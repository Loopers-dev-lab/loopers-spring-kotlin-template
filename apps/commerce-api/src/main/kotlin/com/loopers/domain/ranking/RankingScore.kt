package com.loopers.domain.ranking

import kotlin.math.ln

/**
 * 랭킹 점수 계산을 담당하는 Value Object
 *
 * 가중치 합산(Weighted Sum) 방식으로 점수 계산:
 * Score(p) = W(view) * Count(view) + W(like) * Count(like) + W(order) * Score(order)
 */
data class RankingScore(
    val value: Double,
) {
    init {
        require(value >= 0) { "랭킹 점수는 0 이상이어야 합니다: value=$value" }
    }

    operator fun plus(other: RankingScore): RankingScore {
        return RankingScore(this.value + other.value)
    }

    operator fun times(multiplier: Double): RankingScore {
        return RankingScore(this.value * multiplier)
    }

    companion object {
        /**
         * 가중치 설정
         *
         * - 조회수: 가장 많을 것이므로 낮은 가중치 (0.1)
         * - 좋아요: 구매보다는 덜 중요한 지표 (0.2)
         * - 주문: 실제 구매 행동이므로 가장 높은 가중치 (0.7)
         */
        private const val WEIGHT_VIEW = 0.1
        private const val WEIGHT_LIKE = 0.2
        private const val WEIGHT_ORDER = 0.7

        /**
         * 조회 이벤트 점수
         * Weight: 0.1, Score: 1
         */
        fun fromView(): RankingScore {
            return RankingScore(WEIGHT_VIEW * 1.0)
        }

        /**
         * 좋아요 이벤트 점수
         * Weight: 0.2, Score: 1
         */
        fun fromLike(): RankingScore {
            return RankingScore(WEIGHT_LIKE * 1.0)
        }

        /**
         * 주문 이벤트 점수
         * Weight: 0.7, Score: 정규화된 가격 * 수량
         *
         * 주문 금액이 클수록 점수가 높아지지만, 로그 스케일로 정규화하여
         * 지나치게 높은 금액이 점수를 독식하는 것을 방지
         */
        fun fromOrder(priceAtOrder: Long, quantity: Int): RankingScore {
            val totalAmount = priceAtOrder * quantity
            // 로그 스케일로 정규화 (1 + ln(x))
            val normalizedScore = 1.0 + ln(totalAmount.toDouble())
            return RankingScore(WEIGHT_ORDER * normalizedScore)
        }

        /**
         * 점수 0
         */
        fun zero(): RankingScore {
            return RankingScore(0.0)
        }

        /**
         * 여러 점수를 합산
         */
        fun sum(scores: List<RankingScore>): RankingScore {
            return scores.fold(zero()) { acc, score -> acc + score }
        }
    }
}
