package com.loopers.domain.point

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class AmountTest {

    @ParameterizedTest
    @ValueSource(longs = [-1L, -100L, -1000L, -10000L])
    fun `0미만으로 값으로 Amount 생성 시 예외가 발생한다`(value: Long) {
        assertThatThrownBy {
            Amount(value)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("금액은 0이상 이여야 합니다.")
    }

    @Test
    fun `두 Amount를 더하면 값이 합산된 새로운 Amount가 반환된다`() {
        // given
        val amount1 = Amount(1000L)
        val amount2 = Amount(500L)

        // when
        val result = amount1 + amount2

        // then
        assertThat(result.value).isEqualTo(1500L)
    }

    @Test
    fun `같은 값을 가진 두 Amount는 동등하다`() {
        // given
        val amount1 = Amount(1000L)
        val amount2 = Amount(1000L)

        // when & then
        assertThat(amount1).isEqualTo(amount2)
    }

    @Test
    fun `다른 값을 가진 두 Amount는 동등하지 않다`() {
        // given
        val amount1 = Amount(1000L)
        val amount2 = Amount(2000L)

        // when & then
        assertThat(amount1).isNotEqualTo(amount2)
    }

    @ParameterizedTest(name = "{0}원이 {1}원보다 작은가? => {2}")
    @CsvSource(
        "100, 200, true",
        "200, 100, false",
        "1000, 1000, false",
    )
    fun `두 금액을 비교한다`(baseAmount: Long, comparedAmount: Long, expected: Boolean) {
        // given
        val amount1 = Amount(baseAmount)
        val amount2 = Amount(comparedAmount)

        // when
        val result = amount1.isLessThan(amount2)

        // then
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `금액이 0인지 확인할 수 있다`() {
        // given
        val zeroAmount = Amount(1L) - Amount(1L)

        // when
        val result = zeroAmount.isZero()

        // then
        assertThat(result).isTrue()
    }

    @ParameterizedTest(name = "{0}원은 0이 아니다")
    @ValueSource(longs = [1L, 10L, 100L, 1000L, 10000L])
    fun `0이 아닌 금액은 isZero가 false를 반환한다`(value: Long) {
        // given
        val amount = Amount(value)

        // when
        val result = amount.isZero()

        // then
        assertThat(result).isFalse()
    }
}
