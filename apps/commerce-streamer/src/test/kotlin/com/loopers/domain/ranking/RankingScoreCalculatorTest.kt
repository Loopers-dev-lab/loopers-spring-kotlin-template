package com.loopers.domain.ranking

import com.loopers.domain.order.event.OrderItemDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.ln

@DisplayName("RankingScoreCalculator 테스트")
class RankingScoreCalculatorTest {

    private val weights = RankingWeights(
        view = 0.1,
        like = 0.2,
        unlike = -0.2,
        order = 0.6
    )
    private val calculator = RankingScoreCalculator(weights)

    @DisplayName("주문 아이템 점수는 로그 정규화를 적용한다")
    @Test
    fun calculateOrderItemScoreWithLogNormalization() {
        // given
        val orderItem = OrderItemDto(
            productId = 101L,
            quantity = 2,
            price = 10000L
        )

        // when
        val score = calculator.calculateOrderItemScore(orderItem)

        // then
        val expectedScore = 0.6 * ln(1.0 + 20000.0)
        assertThat(score).isEqualTo(expectedScore)
    }

    @DisplayName("로그 정규화는 고가 상품의 점수 독점을 방지한다")
    @Test
    fun logNormalizationPreventsPriceDomination() {
        // given
        val cheapItem100 = OrderItemDto(productId = 101L, quantity = 100, price = 10000L)
        val expensiveItem1 = OrderItemDto(productId = 102L, quantity = 1, price = 1000000L)

        // when
        val cheapScore = calculator.calculateOrderItemScore(cheapItem100)
        val expensiveScore = calculator.calculateOrderItemScore(expensiveItem1)

        // then
        assertThat(cheapScore).isCloseTo(expensiveScore, org.assertj.core.data.Offset.offset(0.01))
    }

    @DisplayName("구매 횟수가 많을수록 점수가 증가한다")
    @Test
    fun morePurchasesIncreaseScore() {
        // given
        val item1 = OrderItemDto(productId = 101L, quantity = 1, price = 10000L)
        val item100 = OrderItemDto(productId = 101L, quantity = 100, price = 10000L)
        val item1000 = OrderItemDto(productId = 101L, quantity = 1000, price = 10000L)

        // when
        val score1 = calculator.calculateOrderItemScore(item1)
        val score100 = calculator.calculateOrderItemScore(item100)
        val score1000 = calculator.calculateOrderItemScore(item1000)

        // then
        assertThat(score1).isLessThan(score100)
        assertThat(score100).isLessThan(score1000)
    }
}
