package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate

@DisplayName("MvProductRankWeekly")
class MvProductRankWeeklyTest {

    @Nested
    @DisplayName("create 팩토리 메서드는")
    inner class CreateTest {

        @DisplayName("유효한 값으로 엔티티를 생성한다")
        @Test
        fun shouldCreateEntity_whenValidValues() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 2)
            val rank = 1
            val productId = 100L
            val score = BigDecimal("1234.56")

            // act
            val entity = MvProductRankWeekly.create(
                baseDate = baseDate,
                rank = rank,
                productId = productId,
                score = score,
            )

            // assert
            assertAll(
                { assertThat(entity.baseDate).isEqualTo(baseDate) },
                { assertThat(entity.rank).isEqualTo(rank) },
                { assertThat(entity.productId).isEqualTo(productId) },
                { assertThat(entity.score).isEqualByComparingTo(score) },
            )
        }

        @DisplayName("rank가 1일 때 정상 생성된다")
        @Test
        fun shouldCreateEntity_whenRankIsOne() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 2)
            val rank = 1
            val productId = 100L
            val score = BigDecimal("100.00")

            // act
            val entity = MvProductRankWeekly.create(
                baseDate = baseDate,
                rank = rank,
                productId = productId,
                score = score,
            )

            // assert
            assertThat(entity.rank).isEqualTo(1)
        }

        @DisplayName("rank가 100일 때 정상 생성된다")
        @Test
        fun shouldCreateEntity_whenRankIsOneHundred() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 2)
            val rank = 100
            val productId = 100L
            val score = BigDecimal("50.00")

            // act
            val entity = MvProductRankWeekly.create(
                baseDate = baseDate,
                rank = rank,
                productId = productId,
                score = score,
            )

            // assert
            assertThat(entity.rank).isEqualTo(100)
        }

        @DisplayName("score가 0일 때 정상 생성된다")
        @Test
        fun shouldCreateEntity_whenScoreIsZero() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 2)
            val rank = 1
            val productId = 100L
            val score = BigDecimal.ZERO

            // act
            val entity = MvProductRankWeekly.create(
                baseDate = baseDate,
                rank = rank,
                productId = productId,
                score = score,
            )

            // assert
            assertThat(entity.score).isEqualByComparingTo(BigDecimal.ZERO)
        }
    }

    @Nested
    @DisplayName("유효성 검증은")
    inner class ValidationTest {

        @DisplayName("rank가 0일 때 예외가 발생한다")
        @Test
        fun shouldThrowException_whenRankIsZero() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 2)
            val rank = 0
            val productId = 100L
            val score = BigDecimal("100.00")

            // act & assert
            val exception = assertThrows<IllegalArgumentException> {
                MvProductRankWeekly.create(
                    baseDate = baseDate,
                    rank = rank,
                    productId = productId,
                    score = score,
                )
            }

            assertThat(exception.message).contains("rank는 1부터 100 사이의 값이어야 합니다")
        }

        @DisplayName("rank가 101일 때 예외가 발생한다")
        @Test
        fun shouldThrowException_whenRankIsOverOneHundred() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 2)
            val rank = 101
            val productId = 100L
            val score = BigDecimal("100.00")

            // act & assert
            val exception = assertThrows<IllegalArgumentException> {
                MvProductRankWeekly.create(
                    baseDate = baseDate,
                    rank = rank,
                    productId = productId,
                    score = score,
                )
            }

            assertThat(exception.message).contains("rank는 1부터 100 사이의 값이어야 합니다")
        }

        @DisplayName("rank가 음수일 때 예외가 발생한다")
        @Test
        fun shouldThrowException_whenRankIsNegative() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 2)
            val rank = -1
            val productId = 100L
            val score = BigDecimal("100.00")

            // act & assert
            val exception = assertThrows<IllegalArgumentException> {
                MvProductRankWeekly.create(
                    baseDate = baseDate,
                    rank = rank,
                    productId = productId,
                    score = score,
                )
            }

            assertThat(exception.message).contains("rank는 1부터 100 사이의 값이어야 합니다")
        }

        @DisplayName("score가 음수일 때 예외가 발생한다")
        @Test
        fun shouldThrowException_whenScoreIsNegative() {
            // arrange
            val baseDate = LocalDate.of(2025, 1, 2)
            val rank = 1
            val productId = 100L
            val score = BigDecimal("-1.00")

            // act & assert
            val exception = assertThrows<IllegalArgumentException> {
                MvProductRankWeekly.create(
                    baseDate = baseDate,
                    rank = rank,
                    productId = productId,
                    score = score,
                )
            }

            assertThat(exception.message).contains("score는 음수가 될 수 없습니다")
        }
    }
}
