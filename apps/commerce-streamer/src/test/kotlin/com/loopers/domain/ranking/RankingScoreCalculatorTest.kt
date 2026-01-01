package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("RankingScoreCalculator 단위 테스트")
class RankingScoreCalculatorTest {

    private lateinit var calculator: RankingScoreCalculator

    @BeforeEach
    fun setUp() {
        calculator = RankingScoreCalculator()
    }

    @DisplayName("calculate 메서드 테스트")
    @Nested
    inner class Calculate {

        @DisplayName("Score = viewCount x viewWeight + likeCount x likeWeight + orderAmount x orderWeight 공식이 적용된다")
        @Test
        fun `applies score formula correctly`() {
            // given
            val snapshot = CountSnapshot(
                views = 100L,
                likes = 50L,
                orderAmount = BigDecimal("10000.00"),
            )
            val weight = RankingWeight(
                viewWeight = BigDecimal("0.10"),
                likeWeight = BigDecimal("0.20"),
                orderWeight = BigDecimal("0.60"),
            )

            // Score = 100 x 0.10 + 50 x 0.20 + 10000 x 0.60
            //       = 10 + 10 + 6000
            //       = 6020

            // when
            val score = calculator.calculate(snapshot, weight)

            // then
            assertThat(score.value).isEqualByComparingTo(BigDecimal("6020.00"))
        }

        @DisplayName("모든 카운트가 0이면 Score도 0이다")
        @Test
        fun `returns zero score when all counts are zero`() {
            // given
            val snapshot = CountSnapshot(
                views = 0L,
                likes = 0L,
                orderAmount = BigDecimal.ZERO,
            )
            val weight = RankingWeight(
                viewWeight = BigDecimal("0.10"),
                likeWeight = BigDecimal("0.20"),
                orderWeight = BigDecimal("0.60"),
            )

            // when
            val score = calculator.calculate(snapshot, weight)

            // then
            assertThat(score.value).isEqualByComparingTo(BigDecimal.ZERO)
        }

        @DisplayName("viewCount만 있을 때 올바르게 계산된다")
        @Test
        fun `calculates correctly with only viewCount`() {
            // given
            val snapshot = CountSnapshot(
                views = 1000L,
                likes = 0L,
                orderAmount = BigDecimal.ZERO,
            )
            val weight = RankingWeight(
                viewWeight = BigDecimal("0.10"),
                likeWeight = BigDecimal("0.20"),
                orderWeight = BigDecimal("0.60"),
            )

            // Score = 1000 x 0.10 = 100

            // when
            val score = calculator.calculate(snapshot, weight)

            // then
            assertThat(score.value).isEqualByComparingTo(BigDecimal("100.00"))
        }

        @DisplayName("likeCount만 있을 때 올바르게 계산된다")
        @Test
        fun `calculates correctly with only likeCount`() {
            // given
            val snapshot = CountSnapshot(
                views = 0L,
                likes = 200L,
                orderAmount = BigDecimal.ZERO,
            )
            val weight = RankingWeight(
                viewWeight = BigDecimal("0.10"),
                likeWeight = BigDecimal("0.20"),
                orderWeight = BigDecimal("0.60"),
            )

            // Score = 200 x 0.20 = 40

            // when
            val score = calculator.calculate(snapshot, weight)

            // then
            assertThat(score.value).isEqualByComparingTo(BigDecimal("40.00"))
        }

        @DisplayName("orderAmount만 있을 때 올바르게 계산된다")
        @Test
        fun `calculates correctly with only orderAmount`() {
            // given
            val snapshot = CountSnapshot(
                views = 0L,
                likes = 0L,
                orderAmount = BigDecimal("50000.00"),
            )
            val weight = RankingWeight(
                viewWeight = BigDecimal("0.10"),
                likeWeight = BigDecimal("0.20"),
                orderWeight = BigDecimal("0.60"),
            )

            // Score = 50000 x 0.60 = 30000

            // when
            val score = calculator.calculate(snapshot, weight)

            // then
            assertThat(score.value).isEqualByComparingTo(BigDecimal("30000.00"))
        }

        @DisplayName("likeCount가 음수일 때도 올바르게 계산된다")
        @Test
        fun `handles negative likeCount`() {
            // given
            val snapshot = CountSnapshot(
                views = 100L,
                likes = -10L,
                orderAmount = BigDecimal.ZERO,
            )
            val weight = RankingWeight(
                viewWeight = BigDecimal("0.10"),
                likeWeight = BigDecimal("0.20"),
                orderWeight = BigDecimal("0.60"),
            )

            // Score = 100 x 0.10 + (-10) x 0.20 = 10 - 2 = 8

            // when
            val score = calculator.calculate(snapshot, weight)

            // then
            assertThat(score.value).isEqualByComparingTo(BigDecimal("8.00"))
        }

        @DisplayName("계산 결과가 음수가 되면 0으로 보정된다")
        @Test
        fun `clamps negative result to zero`() {
            // given
            val snapshot = CountSnapshot(
                views = 0L,
                likes = -100L,
                orderAmount = BigDecimal.ZERO,
            )
            val weight = RankingWeight(
                viewWeight = BigDecimal("0.10"),
                likeWeight = BigDecimal("0.20"),
                orderWeight = BigDecimal("0.60"),
            )

            // Score = 0 + (-100) x 0.20 + 0 = -20 -> clamped to 0

            // when
            val score = calculator.calculate(snapshot, weight)

            // then
            assertThat(score.value).isEqualByComparingTo(BigDecimal.ZERO)
        }

        @DisplayName("fallback 가중치로 계산할 수 있다")
        @Test
        fun `calculates with fallback weight`() {
            // given
            val snapshot = CountSnapshot(
                views = 100L,
                likes = 50L,
                orderAmount = BigDecimal("10000.00"),
            )
            val weight = RankingWeight.fallback()

            // Score = 100 x 0.10 + 50 x 0.20 + 10000 x 0.60
            //       = 10 + 10 + 6000
            //       = 6020

            // when
            val score = calculator.calculate(snapshot, weight)

            // then
            assertThat(score.value).isEqualByComparingTo(BigDecimal("6020.00"))
        }

        @DisplayName("소수점 반올림이 올바르게 적용된다")
        @Test
        fun `applies rounding correctly`() {
            // given
            val snapshot = CountSnapshot(
                views = 3L,
                likes = 7L,
                orderAmount = BigDecimal("33.33"),
            )
            val weight = RankingWeight(
                viewWeight = BigDecimal("0.10"),
                likeWeight = BigDecimal("0.20"),
                orderWeight = BigDecimal("0.60"),
            )

            // Score = 3 x 0.10 + 7 x 0.20 + 33.33 x 0.60
            //       = 0.3 + 1.4 + 19.998
            //       = 21.698 -> rounded to 21.70

            // when
            val score = calculator.calculate(snapshot, weight)

            // then
            assertThat(score.value).isEqualByComparingTo(BigDecimal("21.70"))
        }
    }
}
