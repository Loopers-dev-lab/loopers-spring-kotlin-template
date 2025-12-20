package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.ln

@DisplayName("RankingScore 단위 테스트")
class RankingScoreTest {

    @Test
    @DisplayName("조회 이벤트 점수 생성 - 기본 가중치")
    fun `should create view score with default weight`() {
        // when
        val score = RankingScore.fromView()

        // then
        assertThat(score.value).isEqualTo(0.1)
    }

    @Test
    @DisplayName("조회 이벤트 점수 생성 - 커스텀 가중치")
    fun `should create view score with custom weight`() {
        // given
        val customWeight = 0.5

        // when
        val score = RankingScore.fromView(customWeight)

        // then
        assertThat(score.value).isEqualTo(0.5)
    }

    @Test
    @DisplayName("좋아요 이벤트 점수 생성 - 기본 가중치")
    fun `should create like score with default weight`() {
        // when
        val score = RankingScore.fromLike()

        // then
        assertThat(score.value).isEqualTo(0.2)
    }

    @Test
    @DisplayName("좋아요 이벤트 점수 생성 - 커스텀 가중치")
    fun `should create like score with custom weight`() {
        // given
        val customWeight = 0.3

        // when
        val score = RankingScore.fromLike(customWeight)

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
        val score = RankingScore.fromOrder(priceAtOrder, quantity)

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
        val score = RankingScore.fromOrder(priceAtOrder, quantity, customWeight)

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
        val score1 = RankingScore.fromOrder(price1, quantity)
        val score10 = RankingScore.fromOrder(price10, quantity)

        // then
        // 금액이 10배 차이나도 점수는 10배가 아니라 로그 스케일로 증가
        val scoreRatio = score10.value / score1.value
        assertThat(scoreRatio).isLessThan(2.0) // 10배가 아닌 약 1.2배
        assertThat(scoreRatio).isGreaterThan(1.0)
    }

    @Test
    @DisplayName("점수 더하기 연산")
    fun `should add scores correctly`() {
        // given
        val score1 = RankingScore(1.5)
        val score2 = RankingScore(2.3)

        // when
        val result = score1 + score2

        // then
        assertThat(result.value).isCloseTo(3.8, org.assertj.core.data.Offset.offset(0.0001))
    }

    @Test
    @DisplayName("점수 곱하기 연산")
    fun `should multiply score correctly`() {
        // given
        val score = RankingScore(10.0)
        val multiplier = 0.1

        // when
        val result = score * multiplier

        // then
        assertThat(result.value).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001))
    }

    @Test
    @DisplayName("점수 0 생성")
    fun `should create zero score`() {
        // when
        val score = RankingScore.zero()

        // then
        assertThat(score.value).isEqualTo(0.0)
    }

    @Test
    @DisplayName("점수 합산")
    fun `should sum multiple scores`() {
        // given
        val scores = listOf(
            RankingScore(1.0),
            RankingScore(2.0),
            RankingScore(3.0),
        )

        // when
        val sum = RankingScore.sum(scores)

        // then
        assertThat(sum.value).isCloseTo(6.0, org.assertj.core.data.Offset.offset(0.0001))
    }

    @Test
    @DisplayName("음수 점수 생성 시 예외 발생")
    fun `should throw exception for negative score`() {
        assertThatThrownBy {
            RankingScore(-1.0)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("랭킹 점수는 0 이상이어야 합니다")
    }
}
