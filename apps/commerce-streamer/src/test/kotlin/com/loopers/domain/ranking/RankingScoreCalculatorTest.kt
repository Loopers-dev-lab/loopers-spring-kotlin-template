package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.ln

@DisplayName("RankingScoreCalculator 단위 테스트")
class RankingScoreCalculatorTest {

    @Test
    @DisplayName("조회 이벤트 점수 생성 - 기본 가중치")
    fun `should create view score with default weight`() {
        // when
        val score = RankingScoreCalculator.fromView()

        // then
        assertThat(score.value).isEqualTo(0.1)
    }

    @Test
    @DisplayName("조회 이벤트 점수 생성 - 커스텀 가중치")
    fun `should create view score with custom weight`() {
        // given
        val customWeight = 0.5

        // when
        val score = RankingScoreCalculator.fromView(customWeight)

        // then
        assertThat(score.value).isEqualTo(0.5)
    }

    @Test
    @DisplayName("좋아요 이벤트 점수 생성 - 기본 가중치")
    fun `should create like score with default weight`() {
        // when
        val score = RankingScoreCalculator.fromLike()

        // then
        assertThat(score.value).isEqualTo(0.2)
    }

    @Test
    @DisplayName("좋아요 이벤트 점수 생성 - 커스텀 가중치")
    fun `should create like score with custom weight`() {
        // given
        val customWeight = 0.3

        // when
        val score = RankingScoreCalculator.fromLike(customWeight)

        // then
        assertThat(score.value).isEqualTo(0.3)
    }

    @Test
    @DisplayName("주문 이벤트 점수 생성 - 기본 가중치, 로그 정규화")
    fun `should create order score with log normalization`() {
        // given
        val priceAtOrder = 100000L
        val quantity = 2
        val totalAmount = 200000L
        val expectedScore = 0.7 * (1.0 + ln(totalAmount.toDouble()))

        // when
        val score = RankingScoreCalculator.fromOrder(priceAtOrder, quantity)

        // then
        assertThat(score.value).isCloseTo(expectedScore, org.assertj.core.data.Offset.offset(0.0001))
    }

    @Test
    @DisplayName("주문 이벤트 점수 생성 - 커스텀 가중치")
    fun `should create order score with custom weight`() {
        // given
        val priceAtOrder = 50000L
        val quantity = 1
        val customWeight = 0.8
        val totalAmount = 50000L
        val expectedScore = customWeight * (1.0 + ln(totalAmount.toDouble()))

        // when
        val score = RankingScoreCalculator.fromOrder(priceAtOrder, quantity, customWeight)

        // then
        assertThat(score.value).isCloseTo(expectedScore, org.assertj.core.data.Offset.offset(0.0001))
    }

    @Test
    @DisplayName("주문 금액에 따른 점수 차이 검증 - 로그 스케일")
    fun `should have logarithmic score difference for order amounts`() {
        // given
        val price1 = 100000L
        val price10 = 1000000L
        val quantity = 1

        // when
        val score1 = RankingScoreCalculator.fromOrder(price1, quantity)
        val score10 = RankingScoreCalculator.fromOrder(price10, quantity)

        // then
        // 금액이 10배 차이나도 점수는 10배가 아니라 로그 스케일로 증가
        val scoreRatio = score10.value / score1.value
        assertThat(scoreRatio).isLessThan(2.0) // 10배가 아닌 약 1.2배
        assertThat(scoreRatio).isGreaterThan(1.0)
    }
}
