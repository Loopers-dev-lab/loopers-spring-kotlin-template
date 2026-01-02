package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

@DisplayName("Score")
class ScoreTest {

    @Nested
    @DisplayName("생성은")
    inner class CreationTest {

        @DisplayName("양수 값으로 Score를 생성한다")
        @Test
        fun shouldCreateScore_whenPositiveValue() {
            // arrange
            val value = BigDecimal("100.50")

            // act
            val score = Score(value)

            // assert
            assertThat(score.value).isEqualByComparingTo(value)
        }

        @DisplayName("0으로 Score를 생성한다")
        @Test
        fun shouldCreateScore_whenZero() {
            // arrange
            val value = BigDecimal.ZERO

            // act
            val score = Score(value)

            // assert
            assertThat(score.value).isEqualByComparingTo(BigDecimal.ZERO)
        }

        @DisplayName("음수 값일 때 예외가 발생한다")
        @Test
        fun shouldThrowException_whenNegativeValue() {
            // arrange
            val value = BigDecimal("-1.00")

            // act & assert
            val exception = assertThrows<IllegalArgumentException> {
                Score(value)
            }

            assertThat(exception.message).contains("Score value cannot be negative")
        }
    }

    @Nested
    @DisplayName("팩토리 메서드는")
    inner class FactoryMethodTest {

        @DisplayName("BigDecimal로 Score를 생성한다")
        @Test
        fun shouldCreateScore_fromBigDecimal() {
            // arrange
            val value = BigDecimal("123.456")

            // act
            val score = Score.of(value)

            // assert
            assertThat(score.value).isEqualByComparingTo(BigDecimal("123.46"))
        }

        @DisplayName("Double로 Score를 생성한다")
        @Test
        fun shouldCreateScore_fromDouble() {
            // arrange
            val value = 123.456

            // act
            val score = Score.of(value)

            // assert
            assertThat(score.value).isEqualByComparingTo(BigDecimal("123.46"))
        }

        @DisplayName("Long으로 Score를 생성한다")
        @Test
        fun shouldCreateScore_fromLong() {
            // arrange
            val value = 100L

            // act
            val score = Score.of(value)

            // assert
            assertThat(score.value).isEqualByComparingTo(BigDecimal("100.00"))
        }

        @DisplayName("ZERO 상수는 0.00을 반환한다")
        @Test
        fun shouldReturnZero_whenUsingZeroConstant() {
            // act
            val score = Score.ZERO

            // assert
            assertThat(score.value).isEqualByComparingTo(BigDecimal("0.00"))
        }
    }

    @Nested
    @DisplayName("applyDecay는")
    inner class ApplyDecayTest {

        @DisplayName("0.9 감쇠 계수를 적용한다")
        @Test
        fun shouldApplyDecay_whenFactorIs0Point9() {
            // arrange
            val score = Score.of(100L)
            val factor = BigDecimal("0.9")

            // act
            val decayed = score.applyDecay(factor)

            // assert
            assertThat(decayed.value).isEqualByComparingTo(BigDecimal("90.00"))
        }

        @DisplayName("0.1 감쇠 계수를 적용한다")
        @Test
        fun shouldApplyDecay_whenFactorIs0Point1() {
            // arrange
            val score = Score.of(100L)
            val factor = BigDecimal("0.1")

            // act
            val decayed = score.applyDecay(factor)

            // assert
            assertThat(decayed.value).isEqualByComparingTo(BigDecimal("10.00"))
        }

        @DisplayName("0 감쇠 계수를 적용하면 0이 된다")
        @Test
        fun shouldReturnZero_whenFactorIsZero() {
            // arrange
            val score = Score.of(100L)
            val factor = BigDecimal.ZERO

            // act
            val decayed = score.applyDecay(factor)

            // assert
            assertThat(decayed.value).isEqualByComparingTo(BigDecimal("0.00"))
        }

        @DisplayName("1.0 감쇠 계수를 적용하면 원래 값이 유지된다")
        @Test
        fun shouldKeepOriginalValue_whenFactorIsOne() {
            // arrange
            val score = Score.of(100L)
            val factor = BigDecimal.ONE

            // act
            val decayed = score.applyDecay(factor)

            // assert
            assertThat(decayed.value).isEqualByComparingTo(BigDecimal("100.00"))
        }

        @DisplayName("음수 감쇠 계수일 때 예외가 발생한다")
        @Test
        fun shouldThrowException_whenFactorIsNegative() {
            // arrange
            val score = Score.of(100L)
            val factor = BigDecimal("-0.1")

            // act & assert
            val exception = assertThrows<IllegalArgumentException> {
                score.applyDecay(factor)
            }

            assertThat(exception.message).contains("Decay factor must be between 0 and 1")
        }

        @DisplayName("1을 초과하는 감쇠 계수일 때 예외가 발생한다")
        @Test
        fun shouldThrowException_whenFactorExceedsOne() {
            // arrange
            val score = Score.of(100L)
            val factor = BigDecimal("1.1")

            // act & assert
            val exception = assertThrows<IllegalArgumentException> {
                score.applyDecay(factor)
            }

            assertThat(exception.message).contains("Decay factor must be between 0 and 1")
        }

        @DisplayName("원본 Score는 변경되지 않는다 (불변성)")
        @Test
        fun shouldNotMutateOriginalScore_whenApplyingDecay() {
            // arrange
            val original = Score.of(100L)
            val factor = BigDecimal("0.5")

            // act
            original.applyDecay(factor)

            // assert
            assertThat(original.value).isEqualByComparingTo(BigDecimal("100.00"))
        }
    }

    @Nested
    @DisplayName("plus 연산자는")
    inner class PlusOperatorTest {

        @DisplayName("두 Score를 더한다")
        @Test
        fun shouldAddTwoScores() {
            // arrange
            val score1 = Score.of(100L)
            val score2 = Score.of(50L)

            // act
            val result = score1 + score2

            // assert
            assertThat(result.value).isEqualByComparingTo(BigDecimal("150.00"))
        }

        @DisplayName("0과 더하면 원래 값이 유지된다")
        @Test
        fun shouldKeepOriginalValue_whenAddingZero() {
            // arrange
            val score = Score.of(100L)

            // act
            val result = score + Score.ZERO

            // assert
            assertThat(result.value).isEqualByComparingTo(BigDecimal("100.00"))
        }

        @DisplayName("소수점 이하 값도 더한다")
        @Test
        fun shouldAddScoresWithDecimalPlaces() {
            // arrange
            val score1 = Score.of(BigDecimal("10.55"))
            val score2 = Score.of(BigDecimal("20.45"))

            // act
            val result = score1 + score2

            // assert
            assertThat(result.value).isEqualByComparingTo(BigDecimal("31.00"))
        }

        @DisplayName("원본 Score들은 변경되지 않는다 (불변성)")
        @Test
        fun shouldNotMutateOriginalScores_whenAdding() {
            // arrange
            val score1 = Score.of(100L)
            val score2 = Score.of(50L)

            // act
            score1 + score2

            // assert
            assertThat(score1.value).isEqualByComparingTo(BigDecimal("100.00"))
            assertThat(score2.value).isEqualByComparingTo(BigDecimal("50.00"))
        }
    }

    @Nested
    @DisplayName("compareTo는")
    inner class CompareToTest {

        @DisplayName("큰 Score가 양수를 반환한다")
        @Test
        fun shouldReturnPositive_whenFirstScoreIsLarger() {
            // arrange
            val larger = Score.of(100L)
            val smaller = Score.of(50L)

            // act & assert
            assertThat(larger > smaller).isTrue()
            assertThat(larger.compareTo(smaller)).isGreaterThan(0)
        }

        @DisplayName("작은 Score가 음수를 반환한다")
        @Test
        fun shouldReturnNegative_whenFirstScoreIsSmaller() {
            // arrange
            val smaller = Score.of(50L)
            val larger = Score.of(100L)

            // act & assert
            assertThat(smaller < larger).isTrue()
            assertThat(smaller.compareTo(larger)).isLessThan(0)
        }

        @DisplayName("같은 Score가 0을 반환한다")
        @Test
        fun shouldReturnZero_whenScoresAreEqual() {
            // arrange
            val score1 = Score.of(100L)
            val score2 = Score.of(100L)

            // act & assert
            assertThat(score1.compareTo(score2)).isEqualTo(0)
        }
    }
}
