package com.loopers.domain.ranking

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Score 값 객체 - 랭킹 점수를 캡슐화
 *
 * - 불변(Immutable) 값 객체
 * - 점수 계산 및 감쇠(decay) 연산 지원
 */
@JvmInline
value class Score(val value: BigDecimal) : Comparable<Score> {

    init {
        require(value >= BigDecimal.ZERO) { "Score value cannot be negative: $value" }
    }

    /**
     * 감쇠 계수를 적용하여 새로운 Score 반환
     * @param factor 감쇠 계수 (0.0 ~ 1.0)
     * @return factor를 곱한 새로운 Score
     */
    fun applyDecay(factor: BigDecimal): Score {
        require(factor >= BigDecimal.ZERO && factor <= BigDecimal.ONE) {
            "Decay factor must be between 0 and 1: $factor"
        }
        return Score(value.multiply(factor).setScale(SCALE, RoundingMode.HALF_UP))
    }

    /**
     * 두 Score를 더한 새로운 Score 반환
     */
    operator fun plus(other: Score): Score {
        return Score(value.add(other.value).setScale(SCALE, RoundingMode.HALF_UP))
    }

    override fun compareTo(other: Score): Int {
        return value.compareTo(other.value)
    }

    companion object {
        private const val SCALE = 2

        val ZERO = Score(BigDecimal.ZERO.setScale(SCALE))

        fun of(value: BigDecimal): Score {
            return Score(value.setScale(SCALE, RoundingMode.HALF_UP))
        }

        fun of(value: Double): Score {
            return Score(BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP))
        }

        fun of(value: Long): Score {
            return Score(BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP))
        }
    }
}
