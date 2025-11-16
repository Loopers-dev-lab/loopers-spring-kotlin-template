package com.loopers.domain.product

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PriceTest {
    @Test
    fun `가격을 생성할 수 있다`() {
        // when
        val price = Price(amount = BigDecimal("10000"), currency = Currency.KRW)

        // then
        assertThat(price.amount).isEqualTo(BigDecimal("10000"))
        assertThat(price.currency).isEqualTo(Currency.KRW)
    }

    @Test
    fun `가격은 0 이상이어야 한다`() {
        assertThatThrownBy {
            Price(amount = BigDecimal("-1"), currency = Currency.KRW)
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("0 이상")
    }

    @Test
    fun `가격을 더할 수 있다`() {
        // given
        val price1 = Price(amount = BigDecimal("10000"), currency = Currency.KRW)
        val price2 = Price(amount = BigDecimal("5000"), currency = Currency.KRW)

        // when
        val result = price1 + price2

        // then
        assertThat(result.amount).isEqualTo(BigDecimal("15000"))
        assertThat(result.currency).isEqualTo(Currency.KRW)
    }

    @Test
    fun `가격을 곱할 수 있다`() {
        // given
        val price = Price(amount = BigDecimal("10000"), currency = Currency.KRW)

        // when
        val result = price * 3

        // then
        assertThat(result.amount).isEqualTo(BigDecimal("30000"))
        assertThat(result.currency).isEqualTo(Currency.KRW)
    }

    @Test
    fun `가격을 비교할 수 있다`() {
        // given
        val price1 = Price(amount = BigDecimal("10000"), currency = Currency.KRW)
        val price2 = Price(amount = BigDecimal("5000"), currency = Currency.KRW)

        // when & then
        assertThat(price1.compareTo(price2)).isPositive()
        assertThat(price2.compareTo(price1)).isNegative()
        assertThat(price1.compareTo(price1)).isZero()
    }

    @Test
    fun `통화가 다른 가격끼리 더하면 예외가 발생한다`() {
        // given
        val krw = Price(amount = BigDecimal("10000"), currency = Currency.KRW)
        val usd = Price(amount = BigDecimal("100"), currency = Currency.USD)

        // when & then
        assertThatThrownBy {
            krw + usd
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("통화")
    }

    @Test
    fun `Value Object이므로 값이 같으면 동일하다`() {
        // given
        val price1 = Price(amount = BigDecimal("10000"), currency = Currency.KRW)
        val price2 = Price(amount = BigDecimal("10000"), currency = Currency.KRW)

        // when & then
        assertThat(price1).isEqualTo(price2)
        assertThat(price1.hashCode()).isEqualTo(price2.hashCode())
    }

    @Test
    fun `단항 플러스 연산자는 자기 자신을 반환한다`() {
        // given
        val price = Price(amount = BigDecimal("10000"), currency = Currency.KRW)

        // when
        val result = +price

        // then
        assertThat(result).isEqualTo(price)
    }

    @Test
    fun `가격을 뺄 수 있다`() {
        // given
        val price1 = Price(amount = BigDecimal("10000"), currency = Currency.KRW)
        val price2 = Price(amount = BigDecimal("3000"), currency = Currency.KRW)

        // when
        val result = price1 - price2

        // then
        assertThat(result.amount).isEqualTo(BigDecimal("7000"))
        assertThat(result.currency).isEqualTo(Currency.KRW)
    }

    @Test
    fun `가격을 빼서 0이 될 수 있다`() {
        // given
        val price1 = Price(amount = BigDecimal("10000"), currency = Currency.KRW)
        val price2 = Price(amount = BigDecimal("10000"), currency = Currency.KRW)

        // when
        val result = price1 - price2

        // then
        assertThat(result.amount).isEqualTo(BigDecimal.ZERO)
        assertThat(result.currency).isEqualTo(Currency.KRW)
    }

    @Test
    fun `가격을 빼서 음수가 되면 예외가 발생한다`() {
        // given
        val price1 = Price(amount = BigDecimal("5000"), currency = Currency.KRW)
        val price2 = Price(amount = BigDecimal("10000"), currency = Currency.KRW)

        // when & then
        assertThatThrownBy {
            price1 - price2
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("0 이상")
    }

    @Test
    fun `통화가 다른 가격끼리 빼면 예외가 발생한다`() {
        // given
        val krw = Price(amount = BigDecimal("10000"), currency = Currency.KRW)
        val usd = Price(amount = BigDecimal("50"), currency = Currency.USD)

        // when & then
        assertThatThrownBy {
            krw - usd
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("통화")
    }
}
