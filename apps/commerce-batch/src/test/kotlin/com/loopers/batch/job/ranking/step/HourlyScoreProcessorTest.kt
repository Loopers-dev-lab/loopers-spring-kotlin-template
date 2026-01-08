package com.loopers.batch.job.ranking.step

import com.loopers.domain.ranking.ProductHourlyMetric
import com.loopers.domain.ranking.RankingScoreCalculator
import com.loopers.domain.ranking.RankingWeight
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

@DisplayName("HourlyScoreProcessor")
class HourlyScoreProcessorTest {

    private lateinit var calculator: RankingScoreCalculator
    private lateinit var weight: RankingWeight

    @BeforeEach
    fun setUp() {
        calculator = RankingScoreCalculator()
        // viewWeight=0.1, likeWeight=0.2, orderWeight=0.6
        weight = RankingWeight(
            viewWeight = BigDecimal("0.10"),
            likeWeight = BigDecimal("0.20"),
            orderWeight = BigDecimal("0.60"),
        )
    }

    @Nested
    @DisplayName("process 메서드는")
    inner class ProcessTest {

        @DisplayName("현재 시간 버킷의 메트릭에 0.9 가중치를 적용한다")
        @Test
        fun shouldApply0_9Weight_forCurrentHourMetric() {
            // arrange
            val currentHour = Instant.parse("2025-01-08T05:00:00Z")
            val processor = HourlyScoreProcessor(calculator, currentHour, weight)

            // 점수 계산: 100*0.1 + 10*0.2 + 1000*0.6 = 10 + 2 + 600 = 612
            // 감쇠 적용: 612 * 0.9 = 550.8
            val metric = ProductHourlyMetric.create(
                statHour = currentHour,
                productId = 100L,
                viewCount = 100L,
                likeCount = 10L,
                orderAmount = BigDecimal("1000"),
            )

            // act
            val result = processor.process(metric)

            // assert
            assertThat(result.productId).isEqualTo(100L)
            assertThat(result.score).isEqualByComparingTo(BigDecimal("550.80"))
        }

        @DisplayName("이전 시간 버킷의 메트릭에 0.1 가중치를 적용한다")
        @Test
        fun shouldApply0_1Weight_forPreviousHourMetric() {
            // arrange
            val currentHour = Instant.parse("2025-01-08T05:00:00Z")
            val previousHour = currentHour.minus(1, ChronoUnit.HOURS)
            val processor = HourlyScoreProcessor(calculator, currentHour, weight)

            // 점수 계산: 100*0.1 + 10*0.2 + 1000*0.6 = 10 + 2 + 600 = 612
            // 감쇠 적용: 612 * 0.1 = 61.2
            val metric = ProductHourlyMetric.create(
                statHour = previousHour,
                productId = 200L,
                viewCount = 100L,
                likeCount = 10L,
                orderAmount = BigDecimal("1000"),
            )

            // act
            val result = processor.process(metric)

            // assert
            assertThat(result.productId).isEqualTo(200L)
            assertThat(result.score).isEqualByComparingTo(BigDecimal("61.20"))
        }

        @DisplayName("동일한 상품의 현재/이전 시간 메트릭이 서로 다른 가중치를 받는다")
        @Test
        fun shouldApplyDifferentWeights_forSameProductDifferentHours() {
            // arrange
            val currentHour = Instant.parse("2025-01-08T05:00:00Z")
            val previousHour = currentHour.minus(1, ChronoUnit.HOURS)
            val processor = HourlyScoreProcessor(calculator, currentHour, weight)

            // 동일한 메트릭 값, 다른 시간
            val currentMetric = ProductHourlyMetric.create(
                statHour = currentHour,
                productId = 100L,
                viewCount = 100L,
                likeCount = 10L,
                orderAmount = BigDecimal("1000"),
            )

            val previousMetric = ProductHourlyMetric.create(
                statHour = previousHour,
                productId = 100L,
                viewCount = 100L,
                likeCount = 10L,
                orderAmount = BigDecimal("1000"),
            )

            // act
            val currentResult = processor.process(currentMetric)
            val previousResult = processor.process(previousMetric)

            // assert
            // 현재: 612 * 0.9 = 550.8
            assertThat(currentResult.score).isEqualByComparingTo(BigDecimal("550.80"))
            // 이전: 612 * 0.1 = 61.2
            assertThat(previousResult.score).isEqualByComparingTo(BigDecimal("61.20"))

            // 가중치 비율 확인 (0.9 : 0.1 = 9 : 1)
            val ratio = currentResult.score.divide(previousResult.score, 2, java.math.RoundingMode.HALF_UP)
            assertThat(ratio).isEqualByComparingTo(BigDecimal("9.00"))
        }

        @DisplayName("모든 값이 0인 경우 점수는 0이다")
        @Test
        fun shouldReturnZeroScore_whenAllValuesAreZero() {
            // arrange
            val currentHour = Instant.parse("2025-01-08T05:00:00Z")
            val processor = HourlyScoreProcessor(calculator, currentHour, weight)

            val metric = ProductHourlyMetric.create(
                statHour = currentHour,
                productId = 300L,
                viewCount = 0L,
                likeCount = 0L,
                orderAmount = BigDecimal.ZERO,
            )

            // act
            val result = processor.process(metric)

            // assert
            assertThat(result.productId).isEqualTo(300L)
            assertThat(result.score).isEqualByComparingTo(BigDecimal.ZERO)
        }

        @DisplayName("likeCount가 음수인 경우도 정상적으로 계산한다")
        @Test
        fun shouldCalculateScore_whenLikeCountIsNegative() {
            // arrange
            val currentHour = Instant.parse("2025-01-08T05:00:00Z")
            val processor = HourlyScoreProcessor(calculator, currentHour, weight)

            // 점수 계산: 100*0.1 + (-10)*0.2 + 1000*0.6 = 10 - 2 + 600 = 608
            // 감쇠 적용: 608 * 0.9 = 547.2
            val metric = ProductHourlyMetric.create(
                statHour = currentHour,
                productId = 400L,
                viewCount = 100L,
                likeCount = -10L,
                orderAmount = BigDecimal("1000"),
            )

            // act
            val result = processor.process(metric)

            // assert
            assertThat(result.productId).isEqualTo(400L)
            assertThat(result.score).isEqualByComparingTo(BigDecimal("547.20"))
        }

        @DisplayName("소수점 2자리로 반올림한다")
        @Test
        fun shouldRoundToTwoDecimalPlaces() {
            // arrange
            val currentHour = Instant.parse("2025-01-08T05:00:00Z")
            val processor = HourlyScoreProcessor(calculator, currentHour, weight)

            // 점수 계산: 3*0.1 + 7*0.2 + 0.01*0.6 = 0.3 + 1.4 + 0.006 = 1.706
            // 감쇠 적용: 1.706 * 0.9 = 1.5354 -> 1.54 (HALF_UP)
            val metric = ProductHourlyMetric.create(
                statHour = currentHour,
                productId = 500L,
                viewCount = 3L,
                likeCount = 7L,
                orderAmount = BigDecimal("0.01"),
            )

            // act
            val result = processor.process(metric)

            // assert
            assertThat(result.productId).isEqualTo(500L)
            assertThat(result.score).isEqualByComparingTo(BigDecimal("1.54"))
        }
    }
}
