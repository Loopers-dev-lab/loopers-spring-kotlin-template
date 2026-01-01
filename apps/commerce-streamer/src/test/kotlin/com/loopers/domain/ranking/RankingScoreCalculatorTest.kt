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

    @DisplayName("calculateForHourly 메서드 테스트")
    @Nested
    inner class CalculateForHourly {

        private val seoulZone = ZoneId.of("Asia/Seoul")
        private val baseHour = ZonedDateTime.of(2024, 1, 1, 10, 0, 0, 0, seoulZone).toInstant()
        private val previousHour = baseHour.minusSeconds(3600)

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
