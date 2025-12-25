package com.loopers.domain.ranking

/**
 * 랭킹 점수 Value Object (공유 타입)
 *
 * 각 모듈별로 특화된 RankingScore 구현을 가질 수 있지만,
 * 이 공유 타입은 기본적인 점수 표현과 연산만 제공합니다.
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
        require(multiplier >= 0) { "배수는 0 이상이어야 합니다: multiplier=$multiplier" }
        return RankingScore(this.value * multiplier)
    }

    companion object {
        private val ZERO = RankingScore(0.0)

        /**
         * 점수 0
         */
        fun zero(): RankingScore {
            return ZERO
        }

        /**
         * 여러 점수를 합산
         */
        fun sum(scores: List<RankingScore>): RankingScore {
            return scores.fold(ZERO) { acc, score -> acc + score }
        }
    }
}
