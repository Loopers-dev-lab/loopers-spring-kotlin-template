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
         * 조회 이벤트 점수
         *
         * @param weight 가중치 (기본값: 0.1)
         */
        fun fromView(weight: Double = 0.1): RankingScore {
            return RankingScore(weight * 1.0)
        }

        /**
         * 좋아요 이벤트 점수
         *
         * @param weight 가중치 (기본값: 0.2)
         */
        fun fromLike(weight: Double = 0.2): RankingScore {
            return RankingScore(weight * 1.0)
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
            val totalAmount = priceAtOrder * quantity
            // 로그 스케일로 정규화 (1 + ln(x))
            val normalizedScore = 1.0 + ln(totalAmount.toDouble())
            return RankingScore(weight * normalizedScore)
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
