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
                orderCount = 10L,
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
                orderCount = 0L,
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
                orderCount = 0L,
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
                orderCount = 0L,
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
                orderCount = 5L,
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
                orderCount = 0L,
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
                orderCount = 0L,
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
                orderCount = 10L,
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
                orderCount = 1L,
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

    @DisplayName("calculateWithDecay 메서드 테스트")
    @Nested
    inner class CalculateWithDecay {

        @DisplayName("newScore = currentScore + previousScore x decayFactor 공식이 적용된다")
        @Test
        fun `applies decay formula correctly`() {
            // given
            val currentScore = Score.of(100.0)
            val previousScore = Score.of(200.0)
            val decayFactor = BigDecimal("0.1")

            // newScore = 100 + 200 x 0.1 = 100 + 20 = 120

            // when
            val result = calculator.calculateWithDecay(currentScore, previousScore, decayFactor)

            // then
            assertThat(result.value).isEqualByComparingTo(BigDecimal("120.00"))
        }

        @DisplayName("currentScore가 0일 때 previousScore의 감쇠 값만 반영된다")
        @Test
        fun `applies only decayed previous score when current is zero`() {
            // given
            val currentScore = Score.ZERO
            val previousScore = Score.of(1000.0)
            val decayFactor = BigDecimal("0.1")

            // newScore = 0 + 1000 x 0.1 = 100

            // when
            val result = calculator.calculateWithDecay(currentScore, previousScore, decayFactor)

            // then
            assertThat(result.value).isEqualByComparingTo(BigDecimal("100.00"))
        }

        @DisplayName("previousScore가 0일 때 currentScore만 반영된다")
        @Test
        fun `returns current score when previous is zero`() {
            // given
            val currentScore = Score.of(150.0)
            val previousScore = Score.ZERO
            val decayFactor = BigDecimal("0.1")

            // newScore = 150 + 0 x 0.1 = 150

            // when
            val result = calculator.calculateWithDecay(currentScore, previousScore, decayFactor)

            // then
            assertThat(result.value).isEqualByComparingTo(BigDecimal("150.00"))
        }

        @DisplayName("decayFactor가 0이면 currentScore만 반영된다")
        @Test
        fun `returns current score when decay factor is zero`() {
            // given
            val currentScore = Score.of(100.0)
            val previousScore = Score.of(500.0)
            val decayFactor = BigDecimal.ZERO

            // newScore = 100 + 500 x 0 = 100

            // when
            val result = calculator.calculateWithDecay(currentScore, previousScore, decayFactor)

            // then
            assertThat(result.value).isEqualByComparingTo(BigDecimal("100.00"))
        }

        @DisplayName("decayFactor가 1이면 두 Score가 합산된다")
        @Test
        fun `returns sum when decay factor is one`() {
            // given
            val currentScore = Score.of(100.0)
            val previousScore = Score.of(200.0)
            val decayFactor = BigDecimal.ONE

            // newScore = 100 + 200 x 1 = 300

            // when
            val result = calculator.calculateWithDecay(currentScore, previousScore, decayFactor)

            // then
            assertThat(result.value).isEqualByComparingTo(BigDecimal("300.00"))
        }

        @DisplayName("버킷 전환 시나리오: 이전 버킷 점수의 10%가 새 버킷에 이월된다")
        @Test
        fun `bucket transition scenario with 10 percent carry over`() {
            // given - 이전 버킷에서 50000점을 획득한 상품
            val currentScore = Score.ZERO // 새 버킷은 시작 시 0점
            val previousScore = Score.of(50000.0)
            val decayFactor = BigDecimal("0.1") // 10% 이월

            // newScore = 0 + 50000 x 0.1 = 5000

            // when
            val result = calculator.calculateWithDecay(currentScore, previousScore, decayFactor)

            // then
            assertThat(result.value).isEqualByComparingTo(BigDecimal("5000.00"))
        }

        @DisplayName("원본 Score들은 변경되지 않는다 (불변성)")
        @Test
        fun `original scores remain unchanged`() {
            // given
            val currentScore = Score.of(100.0)
            val previousScore = Score.of(200.0)
            val decayFactor = BigDecimal("0.1")

            // when
            calculator.calculateWithDecay(currentScore, previousScore, decayFactor)

            // then
            assertThat(currentScore.value).isEqualByComparingTo(BigDecimal("100.00"))
            assertThat(previousScore.value).isEqualByComparingTo(BigDecimal("200.00"))
        }
    }
}
