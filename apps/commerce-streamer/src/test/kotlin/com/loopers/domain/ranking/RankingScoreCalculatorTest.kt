package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("RankingScoreCalculator 점수 계산 테스트")
class RankingScoreCalculatorTest {

    @Test
    @DisplayName("조회수 점수는 0.1이다")
    fun testCalculateViewScoreWhenCalledThenReturns0Point1() {
        // when
        val score = RankingScoreCalculator.calculateViewScore()

        // then
        assertThat(score).isEqualTo(0.1)
    }

    @Test
    @DisplayName("좋아요 점수는 0.2이다")
    fun testCalculateLikeScoreWhenCalledThenReturns0Point2() {
        // when
        val score = RankingScoreCalculator.calculateLikeScore()

        // then
        assertThat(score).isEqualTo(0.2)
    }

    @Test
    @DisplayName("주문 점수는 0.7 × log10(price × quantity)로 계산된다")
    fun testCalculateOrderScoreWhenCalledThenReturnsWeightedScore() {
        // given
        val price = 10000L
        val quantity = 3

        // when
        val score = RankingScoreCalculator.calculateOrderScore(price, quantity)

        // then
        // log10(10000 × 3) = log10(30000) ≈ 4.477
        // 0.7 × 4.477 ≈ 2.686
        assertThat(score).isCloseTo(0.7 * kotlin.math.log10(30000.0), org.assertj.core.data.Offset.offset(0.001))
    }

    @Test
    @DisplayName("주문 점수 계산 시 가격이 0이면 점수도 0이다")
    fun testCalculateOrderScoreWhenPriceIsZeroThenReturnsZero() {
        // given
        val price = 0L
        val quantity = 5

        // when
        val score = RankingScoreCalculator.calculateOrderScore(price, quantity)

        // then
        assertThat(score).isEqualTo(0.0)
    }

    @Test
    @DisplayName("주문 점수 계산 시 수량이 0이면 점수도 0이다")
    fun testCalculateOrderScoreWhenQuantityIsZeroThenReturnsZero() {
        // given
        val price = 50000L
        val quantity = 0

        // when
        val score = RankingScoreCalculator.calculateOrderScore(price, quantity)

        // then
        assertThat(score).isEqualTo(0.0)
    }

    @Test
    @DisplayName("주문 점수는 고가 상품일수록 높다")
    fun testCalculateOrderScoreWhenHighPriceThenReturnsHigherScore() {
        // given
        val lowPriceScore = RankingScoreCalculator.calculateOrderScore(1000L, 1)
        val highPriceScore = RankingScoreCalculator.calculateOrderScore(100000L, 1)

        // then
        assertThat(highPriceScore).isGreaterThan(lowPriceScore)
    }
}
