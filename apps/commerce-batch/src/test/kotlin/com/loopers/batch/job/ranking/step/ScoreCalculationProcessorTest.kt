package com.loopers.batch.job.ranking.step

import com.loopers.domain.ranking.ProductDailyMetric
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

@DisplayName("ScoreCalculationProcessor")
class ScoreCalculationProcessorTest {

    private lateinit var processor: ScoreCalculationProcessor

    @BeforeEach
    fun setUp() {
        processor = ScoreCalculationProcessor()
    }

    @Nested
    @DisplayName("process 메서드는")
    inner class ProcessTest {

        @DisplayName("viewCount * 0.1 + likeCount * 0.2 + orderAmount * 0.6 공식으로 점수를 계산한다")
        @Test
        fun shouldCalculateScoreWithCorrectFormula() {
            // arrange
            // 예시: 1000 * 0.1 + 50 * 0.2 + 100000 * 0.6 = 100 + 10 + 60000 = 60110
            val metric = ProductDailyMetric(
                statDate = LocalDate.of(2025, 1, 1),
                productId = 100L,
                viewCount = 1000L,
                likeCount = 50L,
                orderAmount = BigDecimal("100000"),
            )

            // act
            val result = processor.process(metric)

            // assert
            assertThat(result.productId).isEqualTo(100L)
            assertThat(result.score).isEqualByComparingTo(BigDecimal("60110.00"))
        }

        @DisplayName("모든 값이 0인 경우 점수는 0이다")
        @Test
        fun shouldReturnZeroScore_whenAllValuesAreZero() {
            // arrange
            val metric = ProductDailyMetric(
                statDate = LocalDate.of(2025, 1, 1),
                productId = 200L,
                viewCount = 0L,
                likeCount = 0L,
                orderAmount = BigDecimal.ZERO,
            )

            // act
            val result = processor.process(metric)

            // assert
            assertThat(result.productId).isEqualTo(200L)
            assertThat(result.score).isEqualByComparingTo(BigDecimal.ZERO)
        }

        @DisplayName("likeCount가 음수인 경우도 정상적으로 계산한다")
        @Test
        fun shouldCalculateScore_whenLikeCountIsNegative() {
            // arrange
            // likeCount가 음수일 수 있음 (좋아요 취소가 더 많은 경우)
            val metric = ProductDailyMetric(
                statDate = LocalDate.of(2025, 1, 1),
                productId = 300L,
                viewCount = 100L,
                likeCount = -10L,
                orderAmount = BigDecimal("1000"),
            )

            // act
            val result = processor.process(metric)

            // assert
            // 100 * 0.1 + (-10) * 0.2 + 1000 * 0.6 = 10 - 2 + 600 = 608
            assertThat(result.productId).isEqualTo(300L)
            assertThat(result.score).isEqualByComparingTo(BigDecimal("608.00"))
        }

        @DisplayName("소수점 2자리로 반올림한다")
        @Test
        fun shouldRoundToTwoDecimalPlaces() {
            // arrange
            // 3 * 0.1 = 0.3, 7 * 0.2 = 1.4, 0.01 * 0.6 = 0.006
            val metric = ProductDailyMetric(
                statDate = LocalDate.of(2025, 1, 1),
                productId = 400L,
                viewCount = 3L,
                likeCount = 7L,
                orderAmount = BigDecimal("0.01"),
            )

            // act
            val result = processor.process(metric)

            // assert
            // 0.3 + 1.4 + 0.006 = 1.706 -> 1.71 (HALF_UP)
            assertThat(result.productId).isEqualTo(400L)
            assertThat(result.score).isEqualByComparingTo(BigDecimal("1.71"))
        }
    }
}
