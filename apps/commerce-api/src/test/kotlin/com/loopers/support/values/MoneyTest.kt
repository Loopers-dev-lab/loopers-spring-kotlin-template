package com.loopers.support.values

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.math.BigDecimal

@DisplayName("Money 단위 테스트")
class MoneyTest {

    @DisplayName("Money 생성")
    @Nested
    inner class Creation {

        @DisplayName("Long으로 Money를 생성한다")
        @Test
        fun `create Money from Long`() {
            // when
            val money = Money.krw(10000L)

            // then
            assertThat(money.amount).isEqualByComparingTo(BigDecimal("10000.00"))
        }

        @DisplayName("Int로 Money를 생성한다")
        @Test
        fun `create Money from Int`() {
            // when
            val money = Money.krw(10000)

            // then
            assertThat(money.amount).isEqualByComparingTo(BigDecimal("10000.00"))
        }

        @DisplayName("BigDecimal로 Money를 생성하고 소수점 2자리로 반올림한다")
        @Test
        fun `create Money from BigDecimal with rounding`() {
            // when
            val money = Money.krw(BigDecimal("10000.567"))

            // then
            assertThat(money.amount).isEqualByComparingTo(BigDecimal("10000.57"))
        }

        @DisplayName("ZERO_KRW 상수는 0원을 나타낸다")
        @Test
        fun `ZERO_KRW represents zero amount`() {
            // when
            val zero = Money.ZERO_KRW

            // then
            assertThat(zero.amount).isEqualByComparingTo(BigDecimal.ZERO)
        }
    }

    @DisplayName("사칙연산")
    @Nested
    inner class Arithmetic {

        @DisplayName("Money 덧셈을 올바르게 계산한다")
        @Test
        fun `add two Money values correctly`() {
            // given
            val money1 = Money.krw(10000)
            val money2 = Money.krw(5000)

            // when
            val result = money1 + money2

            // then
            assertThat(result).isEqualTo(Money.krw(15000))
        }

        @DisplayName("Money 뺄셈을 올바르게 계산한다")
        @Test
        fun `subtract two Money values correctly`() {
            // given
            val money1 = Money.krw(10000)
            val money2 = Money.krw(3000)

            // when
            val result = money1 - money2

            // then
            assertThat(result).isEqualTo(Money.krw(7000))
        }

        @DisplayName("Money를 Int로 곱셈한다")
        @Test
        fun `multiply Money by Int`() {
            // given
            val money = Money.krw(10000)

            // when
            val result = money * 3

            // then
            assertThat(result).isEqualTo(Money.krw(30000))
        }

        @DisplayName("Money를 BigDecimal로 곱셈하고 소수점 2자리로 반올림한다")
        @ParameterizedTest(name = "{0}원 * {1} = {2}원")
        @CsvSource(
            "10000, 1.5, 15000.00",
            "10000, 2.25, 22500.00",
            "1000, 1.1, 1100.00",
            "100, 1.234, 123.40",
            "100, 1.2345, 123.45",
            "100, 1.23456, 123.46",
            "100, 1.23454, 123.45",
            "1000, 0.3333, 333.30",
            "1000, 0.33333, 333.33",
        )
        fun `multiply Money by BigDecimal maintains scale 2 with rounding`(
            amount: Long,
            multiplier: String,
            expectedAmount: String,
        ) {
            // given
            val money = Money.krw(amount)

            // when
            val result = money * BigDecimal(multiplier)

            // then
            assertThat(result.amount).isEqualByComparingTo(BigDecimal(expectedAmount))
            assertThat(result.amount.scale()).isEqualTo(2)
        }

        @DisplayName("Money를 Int로 나눗셈하고 소수점 2자리로 반올림한다")
        @ParameterizedTest(name = "{0}원 / {1} = {2}원")
        @CsvSource(
            "10000, 3, 3333.33",
            "10001, 3, 3333.67",
            "100, 7, 14.29",
            "1, 3, 0.33",
            "1000, 3, 333.33",
            "9999, 7, 1428.43",
            "12345, 8, 1543.13",
            "10005, 4, 2501.25",
            "99, 2, 49.50",
        )
        fun `divide Money by Int maintains scale 2 with rounding`(
            amount: Long,
            divisor: Int,
            expectedAmount: String,
        ) {
            // given
            val money = Money.krw(amount)

            // when
            val result = money / divisor

            // then
            assertThat(result.amount).isEqualByComparingTo(BigDecimal(expectedAmount))
            assertThat(result.amount.scale()).isEqualTo(2)
        }

        @DisplayName("0으로 나누면 예외가 발생한다")
        @Test
        fun `throw exception when dividing by zero`() {
            // given
            val money = Money.krw(10000)

            // when & then
            val exception = assertThrows<CoreException> {
                money / 0
            }
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).contains("0으로 나눌 수 없습니다")
        }
    }

    @DisplayName("applyPercentage")
    @Nested
    inner class ApplyPercentage {

        @DisplayName("퍼센티지를 적용하고 소수점 2자리로 반올림한다")
        @ParameterizedTest(name = "{0}원의 {1}% = {2}원")
        @CsvSource(
            "10000, 15, 1500.00",
            "10001, 15, 1500.15",
            "10003, 33, 3300.99",
            "12345, 10, 1234.50",
            "9999, 25, 2499.75",
            "1111, 99, 1099.89",
            "100, 33, 33.00",
            "10007, 17, 1701.19",
            "55555, 7, 3888.85",
        )
        fun `apply percentage maintains scale 2 with rounding`(
            amount: Long,
            percentage: Long,
            expectedAmount: String,
        ) {
            // given
            val money = Money.krw(amount)

            // when
            val result = money.applyPercentage(percentage)

            // then
            assertThat(result.amount).isEqualByComparingTo(BigDecimal(expectedAmount))
            assertThat(result.amount.scale()).isEqualTo(2)
        }

        @DisplayName("0% 퍼센티지를 적용하면 0원이 된다")
        @Test
        fun `apply 0 percentage returns zero`() {
            // given
            val money = Money.krw(10000)

            // when
            val result = money.applyPercentage(0)

            // then
            assertThat(result).isEqualTo(Money.ZERO_KRW)
        }

        @DisplayName("100% 퍼센티지를 적용하면 원래 금액이 된다")
        @Test
        fun `apply 100 percentage returns original amount`() {
            // given
            val money = Money.krw(10000)

            // when
            val result = money.applyPercentage(100)

            // then
            assertThat(result).isEqualTo(money)
        }

        @DisplayName("퍼센티지가 0 미만이면 예외가 발생한다")
        @Test
        fun `throw exception when percentage is negative`() {
            // given
            val money = Money.krw(10000)

            // when & then
            val exception = assertThrows<CoreException> {
                money.applyPercentage(-1)
            }
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).contains("할인율은 0에서 100 사이여야 합니다")
        }

        @DisplayName("퍼센티지가 100 초과이면 예외가 발생한다")
        @Test
        fun `throw exception when percentage exceeds 100`() {
            // given
            val money = Money.krw(10000)

            // when & then
            val exception = assertThrows<CoreException> {
                money.applyPercentage(101)
            }
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).contains("할인율은 0에서 100 사이여야 합니다")
        }
    }

    @DisplayName("min")
    @Nested
    inner class Min {

        @DisplayName("더 작은 Money를 반환한다")
        @Test
        fun `return smaller Money`() {
            // given
            val money1 = Money.krw(10000)
            val money2 = Money.krw(5000)

            // when
            val result = money1.min(money2)

            // then
            assertThat(result).isEqualTo(money2)
        }

        @DisplayName("금액이 같으면 첫 번째 Money를 반환한다")
        @Test
        fun `return first Money when amounts are equal`() {
            // given
            val money1 = Money.krw(10000)
            val money2 = Money.krw(10000)

            // when
            val result = money1.min(money2)

            // then
            assertThat(result).isEqualTo(money1)
        }
    }

    @DisplayName("round")
    @Nested
    inner class Round {

        @DisplayName("0 자리로 반올림한다 (기본값)")
        @Test
        fun `round to 0 decimal places by default`() {
            // given
            val money = Money.krw(BigDecimal("10000.56"))

            // when
            val result = money.round()

            // then
            assertThat(result.amount).isEqualByComparingTo(BigDecimal("10001"))
        }

        @DisplayName("지정된 자리수로 반올림한다")
        @Test
        fun `round to specified decimal places`() {
            // given
            val money = Money.krw(BigDecimal("10000.567"))

            // when
            val result = money.round(1)

            // then
            assertThat(result.amount).isEqualByComparingTo(BigDecimal("10000.6"))
        }
    }

    @DisplayName("비교 연산")
    @Nested
    inner class Comparison {

        @DisplayName("compareTo가 올바르게 작동한다")
        @Test
        fun `compareTo works correctly`() {
            // given
            val money1 = Money.krw(10000)
            val money2 = Money.krw(5000)
            val money3 = Money.krw(10000)

            // when & then
            assertThat(money1.compareTo(money2)).isPositive()
            assertThat(money2.compareTo(money1)).isNegative()
            assertThat(money1.compareTo(money3)).isZero()
        }

        @DisplayName("비교 연산자가 올바르게 작동한다")
        @Test
        fun `comparison operators work correctly`() {
            // given
            val money1 = Money.krw(10000)
            val money2 = Money.krw(5000)
            val money3 = Money.krw(10000)

            // when & then
            assertThat(money1 > money2).isTrue()
            assertThat(money2 < money1).isTrue()
            assertThat(money1 >= money3).isTrue()
            assertThat(money1 <= money3).isTrue()
        }
    }

    @DisplayName("동등성 비교")
    @Nested
    inner class Equality {

        @DisplayName("같은 금액의 Money는 동등하다")
        @Test
        fun `Money with same amount are equal`() {
            // given
            val money1 = Money.krw(10000)
            val money2 = Money.krw(10000)

            // when & then
            assertThat(money1).isEqualTo(money2)
            assertThat(money1.hashCode()).isEqualTo(money2.hashCode())
        }

        @DisplayName("다른 금액의 Money는 동등하지 않다")
        @Test
        fun `Money with different amounts are not equal`() {
            // given
            val money1 = Money.krw(10000)
            val money2 = Money.krw(5000)

            // when & then
            assertThat(money1).isNotEqualTo(money2)
        }

        @DisplayName("소수점 표현이 달라도 같은 금액이면 동등하다")
        @Test
        fun `Money with same amount but different decimal representation are equal`() {
            // given
            val money1 = Money.krw(BigDecimal("10000.00"))
            val money2 = Money.krw(BigDecimal("10000.0"))

            // when & then
            assertThat(money1).isEqualTo(money2)
        }
    }
}
