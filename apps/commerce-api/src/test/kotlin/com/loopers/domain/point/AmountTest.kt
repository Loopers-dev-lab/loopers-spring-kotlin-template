package com.loopers.domain.point

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class AmountTest {

    @ParameterizedTest
    @ValueSource(longs = [0, -1L, -100L, -1000L, -10000L])
    fun `0이하로 값으로 Amount 생성 시 예외가 발생한다`(value: Long) {
        assertThatThrownBy {
            Amount(value)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("충전 금액은 0보다 커야 합니다")
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
}
