package com.loopers.domain.order

import com.loopers.domain.product.Currency
import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MoneyTest {
    @Test
    fun `금액을 생성할 수 있다`() {
        // when
        val money = Money(amount = BigDecimal("50000"), currency = Currency.KRW)

        // then
        assertThat(money.amount).isEqualTo(BigDecimal("50000"))
        assertThat(money.currency).isEqualTo(Currency.KRW)
    }

    @Test
    fun `금액은 0 이상이어야 한다`() {
        assertThatThrownBy {
            Money(amount = BigDecimal("-1"), currency = Currency.KRW)
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("0 이상")
    }

    @Test
    fun `금액을 더할 수 있다`() {
        // given
        val money1 = Money(amount = BigDecimal("10000"), currency = Currency.KRW)
        val money2 = Money(amount = BigDecimal("5000"), currency = Currency.KRW)

        // when
        val result = money1 + money2

        // then
        assertThat(result.amount).isEqualTo(BigDecimal("15000"))
    }

    @Test
    fun `금액을 뺄 수 있다`() {
        // given
        val money1 = Money(amount = BigDecimal("10000"), currency = Currency.KRW)
        val money2 = Money(amount = BigDecimal("3000"), currency = Currency.KRW)

        // when
        val result = money1 - money2

        // then
        assertThat(result.amount).isEqualTo(BigDecimal("7000"))
    }

    @Test
    fun `금액을 곱할 수 있다`() {
        // given
        val money = Money(amount = BigDecimal("10000"), currency = Currency.KRW)

        // when
        val result = money * 3

        // then
        assertThat(result.amount).isEqualTo(BigDecimal("30000"))
    }

    @Test
    fun `Value Object이므로 값이 같으면 동일하다`() {
        // given
        val money1 = Money(amount = BigDecimal("10000"), currency = Currency.KRW)
        val money2 = Money(amount = BigDecimal("10000"), currency = Currency.KRW)

        // when & then
        assertThat(money1).isEqualTo(money2)
        assertThat(money1.hashCode()).isEqualTo(money2.hashCode())
    }

    @Test
    fun `단항 플러스 연산자는 자기 자신을 반환한다`() {
        // given
        val money = Money(amount = BigDecimal("10000"), currency = Currency.KRW)

        // when
        val result = +money

        // then
        assertThat(result).isEqualTo(money)
    }

    @Test
    fun `단항 마이너스 연산자는 예외를 발생시킨다`() {
        // given
        val money = Money(amount = BigDecimal("10000"), currency = Currency.KRW)

        // when & then
        assertThatThrownBy {
            -money
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("0 이상")
    }
}
