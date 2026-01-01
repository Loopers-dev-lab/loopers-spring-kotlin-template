package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

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

    @DisplayName("calculateForHourly 메서드 테스트")
    @Nested
    inner class CalculateForHourly {

        private val seoulZone = ZoneId.of("Asia/Seoul")
        private val baseHour = ZonedDateTime.of(2024, 1, 1, 10, 0, 0, 0, seoulZone)
        private val previousHour = baseHour.minusHours(1)

        @DisplayName("Score 공식이 시간별 메트릭에 올바르게 적용된다")
        @Test
        fun `applies score formula correctly to hourly metrics`() {
            // given
            val currentMetrics = listOf(
                ProductHourlyMetric.create(
                    statHour = baseHour,
                    productId = 1L,
                    viewCount = 100L,
                    likeCount = 50L,
                    orderAmount = BigDecimal("10000.00"),
                ),
            )
            val previousMetrics = listOf(
                ProductHourlyMetric.create(
                    statHour = previousHour,
                    productId = 1L,
                    viewCount = 100L,
                    likeCount = 50L,
                    orderAmount = BigDecimal("10000.00"),
                ),
            )
            val weight = RankingWeight.fallback()

            // Base score = 100 x 0.10 + 50 x 0.20 + 10000 x 0.60 = 6020
            // Decay formula: previous * 0.1 + current * 0.9
            // = 6020 * 0.1 + 6020 * 0.9 = 602 + 5418 = 6020

            // when
            val result = calculator.calculateForHourly(currentMetrics, previousMetrics, weight)

            // then
            assertThat(result).hasSize(1)
            assertThat(result[1L]?.value).isEqualByComparingTo(BigDecimal("6020.00"))
        }

        @DisplayName("감쇠 공식이 올바르게 적용된다: previousScore * 0.1 + currentScore * 0.9")
        @Test
        fun `applies decay formula correctly`() {
            // given
            val currentMetrics = listOf(
                ProductHourlyMetric.create(
                    statHour = baseHour,
                    productId = 1L,
                    viewCount = 1000L,
                    likeCount = 0L,
                    orderAmount = BigDecimal.ZERO,
                ),
            )
            val previousMetrics = listOf(
                ProductHourlyMetric.create(
                    statHour = previousHour,
                    productId = 1L,
                    viewCount = 100L,
                    likeCount = 0L,
                    orderAmount = BigDecimal.ZERO,
                ),
            )
            val weight = RankingWeight.fallback()

            // Current score = 1000 x 0.10 = 100
            // Previous score = 100 x 0.10 = 10
            // Decay formula: 10 * 0.1 + 100 * 0.9 = 1 + 90 = 91

            // when
            val result = calculator.calculateForHourly(currentMetrics, previousMetrics, weight)

            // then
            assertThat(result).hasSize(1)
            assertThat(result[1L]?.value).isEqualByComparingTo(BigDecimal("91.00"))
        }

        @DisplayName("현재 메트릭에만 있는 상품은 score * 0.9를 받는다")
        @Test
        fun `products only in current metrics get score times 0_9`() {
            // given
            val currentMetrics = listOf(
                ProductHourlyMetric.create(
                    statHour = baseHour,
                    productId = 1L,
                    viewCount = 1000L,
                    likeCount = 0L,
                    orderAmount = BigDecimal.ZERO,
                ),
            )
            val previousMetrics = emptyList<ProductHourlyMetric>()
            val weight = RankingWeight.fallback()

            // Current score = 1000 x 0.10 = 100
            // No previous score -> 0
            // Decay formula: 0 * 0.1 + 100 * 0.9 = 90

            // when
            val result = calculator.calculateForHourly(currentMetrics, previousMetrics, weight)

            // then
            assertThat(result).hasSize(1)
            assertThat(result[1L]?.value).isEqualByComparingTo(BigDecimal("90.00"))
        }

        @DisplayName("이전 메트릭에만 있는 상품은 score * 0.1을 받는다")
        @Test
        fun `products only in previous metrics get score times 0_1`() {
            // given
            val currentMetrics = emptyList<ProductHourlyMetric>()
            val previousMetrics = listOf(
                ProductHourlyMetric.create(
                    statHour = previousHour,
                    productId = 1L,
                    viewCount = 1000L,
                    likeCount = 0L,
                    orderAmount = BigDecimal.ZERO,
                ),
            )
            val weight = RankingWeight.fallback()

            // Previous score = 1000 x 0.10 = 100
            // No current score -> 0
            // Decay formula: 100 * 0.1 + 0 * 0.9 = 10

            // when
            val result = calculator.calculateForHourly(currentMetrics, previousMetrics, weight)

            // then
            assertThat(result).hasSize(1)
            assertThat(result[1L]?.value).isEqualByComparingTo(BigDecimal("10.00"))
        }

        @DisplayName("빈 메트릭은 빈 맵을 반환한다")
        @Test
        fun `empty metrics returns empty map`() {
            // given
            val currentMetrics = emptyList<ProductHourlyMetric>()
            val previousMetrics = emptyList<ProductHourlyMetric>()
            val weight = RankingWeight.fallback()

            // when
            val result = calculator.calculateForHourly(currentMetrics, previousMetrics, weight)

            // then
            assertThat(result).isEmpty()
        }

        @DisplayName("여러 상품의 점수가 올바르게 계산된다")
        @Test
        fun `calculates scores for multiple products correctly`() {
            // given
            val currentMetrics = listOf(
                ProductHourlyMetric.create(
                    statHour = baseHour,
                    productId = 1L,
                    viewCount = 100L,
                    likeCount = 0L,
                    orderAmount = BigDecimal.ZERO,
                ),
                ProductHourlyMetric.create(
                    statHour = baseHour,
                    productId = 2L,
                    viewCount = 200L,
                    likeCount = 0L,
                    orderAmount = BigDecimal.ZERO,
                ),
            )
            val previousMetrics = listOf(
                ProductHourlyMetric.create(
                    statHour = previousHour,
                    productId = 2L,
                    viewCount = 100L,
                    likeCount = 0L,
                    orderAmount = BigDecimal.ZERO,
                ),
                ProductHourlyMetric.create(
                    statHour = previousHour,
                    productId = 3L,
                    viewCount = 500L,
                    likeCount = 0L,
                    orderAmount = BigDecimal.ZERO,
                ),
            )
            val weight = RankingWeight.fallback()

            // Product 1: current only -> 100 * 0.10 * 0.9 = 9
            // Product 2: both -> (100 * 0.10) * 0.1 + (200 * 0.10) * 0.9 = 1 + 18 = 19
            // Product 3: previous only -> 500 * 0.10 * 0.1 = 5

            // when
            val result = calculator.calculateForHourly(currentMetrics, previousMetrics, weight)

            // then
            assertThat(result).hasSize(3)
            assertThat(result[1L]?.value).isEqualByComparingTo(BigDecimal("9.00"))
            assertThat(result[2L]?.value).isEqualByComparingTo(BigDecimal("19.00"))
            assertThat(result[3L]?.value).isEqualByComparingTo(BigDecimal("5.00"))
        }
    }

    @DisplayName("calculateForDaily 메서드 테스트")
    @Nested
    inner class CalculateForDaily {

        private val baseDate = LocalDate.of(2024, 1, 1)
        private val previousDate = baseDate.minusDays(1)

        @DisplayName("Score 공식이 일별 메트릭에 올바르게 적용된다")
        @Test
        fun `applies score formula correctly to daily metrics`() {
            // given
            val currentMetrics = listOf(
                ProductDailyMetric.create(
                    statDate = baseDate,
                    productId = 1L,
                    viewCount = 100L,
                    likeCount = 50L,
                    orderAmount = BigDecimal("10000.00"),
                ),
            )
            val previousMetrics = listOf(
                ProductDailyMetric.create(
                    statDate = previousDate,
                    productId = 1L,
                    viewCount = 100L,
                    likeCount = 50L,
                    orderAmount = BigDecimal("10000.00"),
                ),
            )
            val weight = RankingWeight.fallback()

            // Base score = 100 x 0.10 + 50 x 0.20 + 10000 x 0.60 = 6020
            // Decay formula: previous * 0.1 + current * 0.9
            // = 6020 * 0.1 + 6020 * 0.9 = 602 + 5418 = 6020

            // when
            val result = calculator.calculateForDaily(currentMetrics, previousMetrics, weight)

            // then
            assertThat(result).hasSize(1)
            assertThat(result[1L]?.value).isEqualByComparingTo(BigDecimal("6020.00"))
        }

        @DisplayName("감쇠 공식이 시간별과 동일하게 적용된다")
        @Test
        fun `applies decay formula identical to hourly`() {
            // given
            val currentMetrics = listOf(
                ProductDailyMetric.create(
                    statDate = baseDate,
                    productId = 1L,
                    viewCount = 1000L,
                    likeCount = 0L,
                    orderAmount = BigDecimal.ZERO,
                ),
            )
            val previousMetrics = listOf(
                ProductDailyMetric.create(
                    statDate = previousDate,
                    productId = 1L,
                    viewCount = 100L,
                    likeCount = 0L,
                    orderAmount = BigDecimal.ZERO,
                ),
            )
            val weight = RankingWeight.fallback()

            // Current score = 1000 x 0.10 = 100
            // Previous score = 100 x 0.10 = 10
            // Decay formula: 10 * 0.1 + 100 * 0.9 = 1 + 90 = 91

            // when
            val result = calculator.calculateForDaily(currentMetrics, previousMetrics, weight)

            // then
            assertThat(result).hasSize(1)
            assertThat(result[1L]?.value).isEqualByComparingTo(BigDecimal("91.00"))
        }

        @DisplayName("이전 날 데이터가 없는 콜드 스타트의 경우 current * 0.9만 적용된다")
        @Test
        fun `cold start case with no previous day data`() {
            // given
            val currentMetrics = listOf(
                ProductDailyMetric.create(
                    statDate = baseDate,
                    productId = 1L,
                    viewCount = 1000L,
                    likeCount = 0L,
                    orderAmount = BigDecimal.ZERO,
                ),
            )
            val previousMetrics = emptyList<ProductDailyMetric>()
            val weight = RankingWeight.fallback()

            // Current score = 1000 x 0.10 = 100
            // No previous score -> 0
            // Decay formula: 0 * 0.1 + 100 * 0.9 = 90

            // when
            val result = calculator.calculateForDaily(currentMetrics, previousMetrics, weight)

            // then
            assertThat(result).hasSize(1)
            assertThat(result[1L]?.value).isEqualByComparingTo(BigDecimal("90.00"))
        }

        @DisplayName("빈 메트릭은 빈 맵을 반환한다")
        @Test
        fun `empty metrics returns empty map`() {
            // given
            val currentMetrics = emptyList<ProductDailyMetric>()
            val previousMetrics = emptyList<ProductDailyMetric>()
            val weight = RankingWeight.fallback()

            // when
            val result = calculator.calculateForDaily(currentMetrics, previousMetrics, weight)

            // then
            assertThat(result).isEmpty()
        }
    }
}
