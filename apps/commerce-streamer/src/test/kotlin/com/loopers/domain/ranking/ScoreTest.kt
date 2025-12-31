package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("Score 단위 테스트")
class ScoreTest {

    @DisplayName("생성자 테스트")
    @Nested
    inner class Constructor {

        @DisplayName("양수 값으로 Score를 생성할 수 있다")
        @Test
        fun `creates Score with positive value`() {
            // given
            val value = BigDecimal("100.50")

            // when
            val score = Score(value)

            // then
            assertThat(score.value).isEqualByComparingTo(value)
        }

        @DisplayName("0으로 Score를 생성할 수 있다")
        @Test
        fun `creates Score with zero value`() {
            // given
            val value = BigDecimal.ZERO

            // when
            val score = Score(value)

            // then
            assertThat(score.value).isEqualByComparingTo(BigDecimal.ZERO)
        }

        @DisplayName("음수 값으로 Score를 생성하면 예외가 발생한다")
        @Test
        fun `throws exception when value is negative`() {
            // given
            val negativeValue = BigDecimal("-1.00")

            // when & then
            assertThatThrownBy { Score(negativeValue) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("cannot be negative")
        }
    }

    @DisplayName("팩토리 메서드 테스트")
    @Nested
    inner class FactoryMethod {

        @DisplayName("BigDecimal로 Score를 생성한다")
        @Test
        fun `creates Score from BigDecimal`() {
            // given
            val value = BigDecimal("123.456")

            // when
            val score = Score.of(value)

            // then
            assertThat(score.value).isEqualByComparingTo(BigDecimal("123.46"))
        }

        @DisplayName("Double로 Score를 생성한다")
        @Test
        fun `creates Score from Double`() {
            // given
            val value = 99.99

            // when
            val score = Score.of(value)

            // then
            assertThat(score.value).isEqualByComparingTo(BigDecimal("99.99"))
        }

        @DisplayName("Long으로 Score를 생성한다")
        @Test
        fun `creates Score from Long`() {
            // given
            val value = 100L

            // when
            val score = Score.of(value)

            // then
            assertThat(score.value).isEqualByComparingTo(BigDecimal("100.00"))
        }

        @DisplayName("ZERO 상수는 0.00 값이다")
        @Test
        fun `ZERO constant equals zero`() {
            // when
            val zero = Score.ZERO

            // then
            assertThat(zero.value).isEqualByComparingTo(BigDecimal.ZERO)
        }
    }

    @DisplayName("applyDecay 메서드 테스트")
    @Nested
    inner class ApplyDecay {

        @DisplayName("decay factor 0.1을 적용하면 점수가 10%로 감소한다")
        @Test
        fun `applies decay factor of 0_1`() {
            // given
            val score = Score.of(100.0)
            val decayFactor = BigDecimal("0.1")

            // when
            val decayedScore = score.applyDecay(decayFactor)

            // then
            assertThat(decayedScore.value).isEqualByComparingTo(BigDecimal("10.00"))
        }

        @DisplayName("decay factor 0.5를 적용하면 점수가 50%로 감소한다")
        @Test
        fun `applies decay factor of 0_5`() {
            // given
            val score = Score.of(200.0)
            val decayFactor = BigDecimal("0.5")

            // when
            val decayedScore = score.applyDecay(decayFactor)

            // then
            assertThat(decayedScore.value).isEqualByComparingTo(BigDecimal("100.00"))
        }

        @DisplayName("decay factor 0을 적용하면 점수가 0이 된다")
        @Test
        fun `applies decay factor of zero`() {
            // given
            val score = Score.of(100.0)
            val decayFactor = BigDecimal.ZERO

            // when
            val decayedScore = score.applyDecay(decayFactor)

            // then
            assertThat(decayedScore.value).isEqualByComparingTo(BigDecimal.ZERO)
        }

        @DisplayName("decay factor 1을 적용하면 점수가 유지된다")
        @Test
        fun `applies decay factor of one`() {
            // given
            val score = Score.of(100.0)
            val decayFactor = BigDecimal.ONE

            // when
            val decayedScore = score.applyDecay(decayFactor)

            // then
            assertThat(decayedScore.value).isEqualByComparingTo(BigDecimal("100.00"))
        }

        @DisplayName("decay factor가 음수이면 예외가 발생한다")
        @Test
        fun `throws exception when decay factor is negative`() {
            // given
            val score = Score.of(100.0)
            val negativeFactor = BigDecimal("-0.1")

            // when & then
            assertThatThrownBy { score.applyDecay(negativeFactor) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("between 0 and 1")
        }

        @DisplayName("decay factor가 1을 초과하면 예외가 발생한다")
        @Test
        fun `throws exception when decay factor exceeds one`() {
            // given
            val score = Score.of(100.0)
            val largeFactor = BigDecimal("1.1")

            // when & then
            assertThatThrownBy { score.applyDecay(largeFactor) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("between 0 and 1")
        }

        @DisplayName("원본 Score는 변경되지 않는다 (불변성)")
        @Test
        fun `original Score remains unchanged`() {
            // given
            val original = Score.of(100.0)
            val decayFactor = BigDecimal("0.1")

            // when
            val decayed = original.applyDecay(decayFactor)

            // then
            assertThat(original.value).isEqualByComparingTo(BigDecimal("100.00"))
            assertThat(decayed.value).isEqualByComparingTo(BigDecimal("10.00"))
        }
    }

    @DisplayName("plus 연산자 테스트")
    @Nested
    inner class Plus {

        @DisplayName("두 Score를 더하면 합계가 반환된다")
        @Test
        fun `adds two scores`() {
            // given
            val score1 = Score.of(100.0)
            val score2 = Score.of(50.0)

            // when
            val result = score1 + score2

            // then
            assertThat(result.value).isEqualByComparingTo(BigDecimal("150.00"))
        }

        @DisplayName("Score에 ZERO를 더하면 원래 값이 유지된다")
        @Test
        fun `adding ZERO returns original value`() {
            // given
            val score = Score.of(100.0)

            // when
            val result = score + Score.ZERO

            // then
            assertThat(result.value).isEqualByComparingTo(BigDecimal("100.00"))
        }

        @DisplayName("여러 Score를 연속으로 더할 수 있다")
        @Test
        fun `adds multiple scores in chain`() {
            // given
            val score1 = Score.of(10.0)
            val score2 = Score.of(20.0)
            val score3 = Score.of(30.0)

            // when
            val result = score1 + score2 + score3

            // then
            assertThat(result.value).isEqualByComparingTo(BigDecimal("60.00"))
        }

        @DisplayName("원본 Score들은 변경되지 않는다 (불변성)")
        @Test
        fun `original Scores remain unchanged`() {
            // given
            val score1 = Score.of(100.0)
            val score2 = Score.of(50.0)

            // when
            val result = score1 + score2

            // then
            assertThat(score1.value).isEqualByComparingTo(BigDecimal("100.00"))
            assertThat(score2.value).isEqualByComparingTo(BigDecimal("50.00"))
            assertThat(result.value).isEqualByComparingTo(BigDecimal("150.00"))
        }
    }

    @DisplayName("compareTo 테스트")
    @Nested
    inner class CompareTo {

        @DisplayName("더 큰 Score가 더 크다고 판단된다")
        @Test
        fun `larger Score is greater`() {
            // given
            val larger = Score.of(100.0)
            val smaller = Score.of(50.0)

            // when & then
            assertThat(larger).isGreaterThan(smaller)
            assertThat(smaller).isLessThan(larger)
        }

        @DisplayName("같은 값의 Score는 동등하다")
        @Test
        fun `equal Scores are equal`() {
            // given
            val score1 = Score.of(100.0)
            val score2 = Score.of(100.0)

            // when & then
            assertThat(score1.compareTo(score2)).isEqualTo(0)
        }

        @DisplayName("Score 리스트를 정렬할 수 있다")
        @Test
        fun `can sort list of Scores`() {
            // given
            val scores = listOf(
                Score.of(30.0),
                Score.of(10.0),
                Score.of(50.0),
                Score.of(20.0),
            )

            // when
            val sorted = scores.sorted()

            // then
            assertThat(sorted[0].value).isEqualByComparingTo(BigDecimal("10.00"))
            assertThat(sorted[1].value).isEqualByComparingTo(BigDecimal("20.00"))
            assertThat(sorted[2].value).isEqualByComparingTo(BigDecimal("30.00"))
            assertThat(sorted[3].value).isEqualByComparingTo(BigDecimal("50.00"))
        }
    }
}
