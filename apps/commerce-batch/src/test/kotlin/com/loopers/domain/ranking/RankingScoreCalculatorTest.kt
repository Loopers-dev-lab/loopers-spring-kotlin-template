package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@DisplayName("RankingScoreCalculator")
class RankingScoreCalculatorTest {

    private lateinit var calculator: RankingScoreCalculator
    private lateinit var weight: RankingWeight

    @BeforeEach
    fun setUp() {
        calculator = RankingScoreCalculator()
        weight = RankingWeight(
            viewWeight = BigDecimal("0.10"),
            likeWeight = BigDecimal("0.20"),
            orderWeight = BigDecimal("0.60"),
        )
    }

    @Nested
    @DisplayName("calculateSingleHourly는")
    inner class CalculateSingleHourlyTest {

        @DisplayName("현재 시간 버킷의 메트릭에 0.9 가중치를 적용한다")
        @Test
        fun shouldApply0Point9Weight_whenMetricIsInCurrentHourBucket() {
            // arrange
            val currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val metric = ProductHourlyMetric.create(
                statHour = currentHour,
                productId = 1L,
                viewCount = 100L,
                likeCount = 50L,
                orderAmount = BigDecimal("1000.00"),
            )
            // rawScore = 100 * 0.1 + 50 * 0.2 + 1000 * 0.6 = 10 + 10 + 600 = 620
            // decayedScore = 620 * 0.9 = 558.00

            // act
            val result = calculator.calculateSingleHourly(metric, currentHour, weight)

            // assert
            assertThat(result.productId).isEqualTo(1L)
            assertThat(result.score).isEqualByComparingTo(BigDecimal("558.00"))
        }

        @DisplayName("이전 시간 버킷의 메트릭에 0.1 가중치를 적용한다")
        @Test
        fun shouldApply0Point1Weight_whenMetricIsInPreviousHourBucket() {
            // arrange
            val currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val previousHour = currentHour.minus(1, ChronoUnit.HOURS)
            val metric = ProductHourlyMetric.create(
                statHour = previousHour,
                productId = 2L,
                viewCount = 100L,
                likeCount = 50L,
                orderAmount = BigDecimal("1000.00"),
            )
            // rawScore = 100 * 0.1 + 50 * 0.2 + 1000 * 0.6 = 10 + 10 + 600 = 620
            // decayedScore = 620 * 0.1 = 62.00

            // act
            val result = calculator.calculateSingleHourly(metric, currentHour, weight)

            // assert
            assertThat(result.productId).isEqualTo(2L)
            assertThat(result.score).isEqualByComparingTo(BigDecimal("62.00"))
        }

        @DisplayName("동일 상품의 현재/이전 버킷 점수 비율이 9:1이다")
        @Test
        fun shouldHave9To1Ratio_betweenCurrentAndPreviousBuckets() {
            // arrange
            val currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val previousHour = currentHour.minus(1, ChronoUnit.HOURS)

            val currentMetric = ProductHourlyMetric.create(
                statHour = currentHour,
                productId = 1L,
                viewCount = 100L,
                likeCount = 50L,
                orderAmount = BigDecimal("1000.00"),
            )
            val previousMetric = ProductHourlyMetric.create(
                statHour = previousHour,
                productId = 1L,
                viewCount = 100L,
                likeCount = 50L,
                orderAmount = BigDecimal("1000.00"),
            )

            // act
            val currentResult = calculator.calculateSingleHourly(currentMetric, currentHour, weight)
            val previousResult = calculator.calculateSingleHourly(previousMetric, currentHour, weight)

            // assert
            // currentResult.score / previousResult.score = 558 / 62 = 9
            val ratio = currentResult.score.divide(previousResult.score, 0, java.math.RoundingMode.HALF_UP)
            assertThat(ratio).isEqualByComparingTo(BigDecimal("9"))
        }

        @DisplayName("조회수만 있는 경우 점수를 계산한다")
        @Test
        fun shouldCalculateScore_whenOnlyViewCountExists() {
            // arrange
            val currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val metric = ProductHourlyMetric.create(
                statHour = currentHour,
                productId = 1L,
                viewCount = 100L,
                likeCount = 0L,
                orderAmount = BigDecimal.ZERO,
            )
            // rawScore = 100 * 0.1 + 0 * 0.2 + 0 * 0.6 = 10
            // decayedScore = 10 * 0.9 = 9.00

            // act
            val result = calculator.calculateSingleHourly(metric, currentHour, weight)

            // assert
            assertThat(result.score).isEqualByComparingTo(BigDecimal("9.00"))
        }

        @DisplayName("모든 메트릭이 0인 경우 점수가 0이다")
        @Test
        fun shouldReturnZeroScore_whenAllMetricsAreZero() {
            // arrange
            val currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val metric = ProductHourlyMetric.create(
                statHour = currentHour,
                productId = 1L,
                viewCount = 0L,
                likeCount = 0L,
                orderAmount = BigDecimal.ZERO,
            )

            // act
            val result = calculator.calculateSingleHourly(metric, currentHour, weight)

            // assert
            assertThat(result.score).isEqualByComparingTo(BigDecimal("0.00"))
        }
    }

    @Nested
    @DisplayName("calculateSingleDaily는")
    inner class CalculateSingleDailyTest {

        @DisplayName("기준 날짜와 같은 날짜의 메트릭에 0.9 가중치를 적용한다")
        @Test
        fun shouldApply0Point9Weight_whenMetricIsOnBaseDate() {
            // arrange
            val baseDate = LocalDate.now()
            val metric = ProductDailyMetric(
                statDate = baseDate,
                productId = 1L,
                viewCount = 100L,
                likeCount = 50L,
                orderAmount = BigDecimal("1000.00"),
            )
            // rawScore = 100 * 0.1 + 50 * 0.2 + 1000 * 0.6 = 10 + 10 + 600 = 620
            // decayedScore = 620 * 0.9 = 558.00

            // act
            val result = calculator.calculateSingleDaily(metric, baseDate, weight)

            // assert
            assertThat(result.productId).isEqualTo(1L)
            assertThat(result.score).isEqualByComparingTo(BigDecimal("558.00"))
        }

        @DisplayName("이전 날짜의 메트릭에 0.1 가중치를 적용한다")
        @Test
        fun shouldApply0Point1Weight_whenMetricIsOnPreviousDate() {
            // arrange
            val baseDate = LocalDate.now()
            val previousDate = baseDate.minusDays(1)
            val metric = ProductDailyMetric(
                statDate = previousDate,
                productId = 2L,
                viewCount = 100L,
                likeCount = 50L,
                orderAmount = BigDecimal("1000.00"),
            )
            // rawScore = 100 * 0.1 + 50 * 0.2 + 1000 * 0.6 = 10 + 10 + 600 = 620
            // decayedScore = 620 * 0.1 = 62.00

            // act
            val result = calculator.calculateSingleDaily(metric, baseDate, weight)

            // assert
            assertThat(result.productId).isEqualTo(2L)
            assertThat(result.score).isEqualByComparingTo(BigDecimal("62.00"))
        }

        @DisplayName("동일 상품의 현재/이전 날짜 점수 비율이 9:1이다")
        @Test
        fun shouldHave9To1Ratio_betweenCurrentAndPreviousDates() {
            // arrange
            val baseDate = LocalDate.now()
            val previousDate = baseDate.minusDays(1)

            val currentMetric = ProductDailyMetric(
                statDate = baseDate,
                productId = 1L,
                viewCount = 100L,
                likeCount = 50L,
                orderAmount = BigDecimal("1000.00"),
            )
            val previousMetric = ProductDailyMetric(
                statDate = previousDate,
                productId = 1L,
                viewCount = 100L,
                likeCount = 50L,
                orderAmount = BigDecimal("1000.00"),
            )

            // act
            val currentResult = calculator.calculateSingleDaily(currentMetric, baseDate, weight)
            val previousResult = calculator.calculateSingleDaily(previousMetric, baseDate, weight)

            // assert
            // currentResult.score / previousResult.score = 558 / 62 = 9
            val ratio = currentResult.score.divide(previousResult.score, 0, java.math.RoundingMode.HALF_UP)
            assertThat(ratio).isEqualByComparingTo(BigDecimal("9"))
        }

        @DisplayName("주문 금액만 있는 경우 점수를 계산한다")
        @Test
        fun shouldCalculateScore_whenOnlyOrderAmountExists() {
            // arrange
            val baseDate = LocalDate.now()
            val metric = ProductDailyMetric(
                statDate = baseDate,
                productId = 1L,
                viewCount = 0L,
                likeCount = 0L,
                orderAmount = BigDecimal("1000.00"),
            )
            // rawScore = 0 * 0.1 + 0 * 0.2 + 1000 * 0.6 = 600
            // decayedScore = 600 * 0.9 = 540.00

            // act
            val result = calculator.calculateSingleDaily(metric, baseDate, weight)

            // assert
            assertThat(result.score).isEqualByComparingTo(BigDecimal("540.00"))
        }

        @DisplayName("모든 메트릭이 0인 경우 점수가 0이다")
        @Test
        fun shouldReturnZeroScore_whenAllMetricsAreZero() {
            // arrange
            val baseDate = LocalDate.now()
            val metric = ProductDailyMetric(
                statDate = baseDate,
                productId = 1L,
                viewCount = 0L,
                likeCount = 0L,
                orderAmount = BigDecimal.ZERO,
            )

            // act
            val result = calculator.calculateSingleDaily(metric, baseDate, weight)

            // assert
            assertThat(result.score).isEqualByComparingTo(BigDecimal("0.00"))
        }

        @DisplayName("2일 이전 날짜의 메트릭에도 0.1 가중치를 적용한다")
        @Test
        fun shouldApply0Point1Weight_whenMetricIsTwoDaysOld() {
            // arrange
            val baseDate = LocalDate.now()
            val twoDaysAgo = baseDate.minusDays(2)
            val metric = ProductDailyMetric(
                statDate = twoDaysAgo,
                productId = 1L,
                viewCount = 100L,
                likeCount = 50L,
                orderAmount = BigDecimal("1000.00"),
            )
            // rawScore = 620, decayedScore = 620 * 0.1 = 62.00

            // act
            val result = calculator.calculateSingleDaily(metric, baseDate, weight)

            // assert
            assertThat(result.score).isEqualByComparingTo(BigDecimal("62.00"))
        }
    }

    @Nested
    @DisplayName("calculateForHourly는")
    inner class CalculateForHourlyTest {

        @DisplayName("현재/이전 시간 메트릭에 감쇠 공식을 적용한다")
        @Test
        fun shouldApplyDecayFormula_toCurrentAndPreviousMetrics() {
            // arrange
            val currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val previousHour = currentHour.minus(1, ChronoUnit.HOURS)

            val currentMetrics = listOf(
                ProductHourlyMetric.create(
                    statHour = currentHour,
                    productId = 1L,
                    viewCount = 100L,
                    likeCount = 50L,
                    orderAmount = BigDecimal("1000.00"),
                ),
            )
            val previousMetrics = listOf(
                ProductHourlyMetric.create(
                    statHour = previousHour,
                    productId = 1L,
                    viewCount = 100L,
                    likeCount = 50L,
                    orderAmount = BigDecimal("1000.00"),
                ),
            )
            // rawScore = 620
            // combinedScore = 620 * 0.9 + 620 * 0.1 = 558 + 62 = 620

            // act
            val result = calculator.calculateForHourly(currentMetrics, previousMetrics, weight)

            // assert
            assertThat(result).hasSize(1)
            assertThat(result[1L]?.value).isEqualByComparingTo(BigDecimal("620.00"))
        }

        @DisplayName("현재 메트릭만 있는 경우 0.9 가중치만 적용한다")
        @Test
        fun shouldApplyOnlyCurrentWeight_whenOnlyCurrentMetricsExist() {
            // arrange
            val currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS)

            val currentMetrics = listOf(
                ProductHourlyMetric.create(
                    statHour = currentHour,
                    productId = 1L,
                    viewCount = 100L,
                    likeCount = 50L,
                    orderAmount = BigDecimal("1000.00"),
                ),
            )
            val previousMetrics = emptyList<ProductHourlyMetric>()
            // rawScore = 620
            // combinedScore = 620 * 0.9 + 0 * 0.1 = 558 + 0 = 558

            // act
            val result = calculator.calculateForHourly(currentMetrics, previousMetrics, weight)

            // assert
            assertThat(result).hasSize(1)
            assertThat(result[1L]?.value).isEqualByComparingTo(BigDecimal("558.00"))
        }

        @DisplayName("이전 메트릭만 있는 경우 0.1 가중치만 적용한다")
        @Test
        fun shouldApplyOnlyDecayFactor_whenOnlyPreviousMetricsExist() {
            // arrange
            val currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS)
            val previousHour = currentHour.minus(1, ChronoUnit.HOURS)

            val currentMetrics = emptyList<ProductHourlyMetric>()
            val previousMetrics = listOf(
                ProductHourlyMetric.create(
                    statHour = previousHour,
                    productId = 1L,
                    viewCount = 100L,
                    likeCount = 50L,
                    orderAmount = BigDecimal("1000.00"),
                ),
            )
            // rawScore = 620
            // combinedScore = 0 * 0.9 + 620 * 0.1 = 0 + 62 = 62

            // act
            val result = calculator.calculateForHourly(currentMetrics, previousMetrics, weight)

            // assert
            assertThat(result).hasSize(1)
            assertThat(result[1L]?.value).isEqualByComparingTo(BigDecimal("62.00"))
        }
    }

    @Nested
    @DisplayName("calculateForDaily는")
    inner class CalculateForDailyTest {

        @DisplayName("현재/이전 날짜 메트릭에 감쇠 공식을 적용한다")
        @Test
        fun shouldApplyDecayFormula_toCurrentAndPreviousMetrics() {
            // arrange
            val today = LocalDate.now()
            val yesterday = today.minusDays(1)

            val currentMetrics = listOf(
                ProductDailyMetric(
                    statDate = today,
                    productId = 1L,
                    viewCount = 100L,
                    likeCount = 50L,
                    orderAmount = BigDecimal("1000.00"),
                ),
            )
            val previousMetrics = listOf(
                ProductDailyMetric(
                    statDate = yesterday,
                    productId = 1L,
                    viewCount = 100L,
                    likeCount = 50L,
                    orderAmount = BigDecimal("1000.00"),
                ),
            )
            // rawScore = 620
            // combinedScore = 620 * 0.9 + 620 * 0.1 = 558 + 62 = 620

            // act
            val result = calculator.calculateForDaily(currentMetrics, previousMetrics, weight)

            // assert
            assertThat(result).hasSize(1)
            assertThat(result[1L]?.value).isEqualByComparingTo(BigDecimal("620.00"))
        }
    }
}
