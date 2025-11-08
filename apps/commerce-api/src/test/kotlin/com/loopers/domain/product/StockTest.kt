package com.loopers.domain.product

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class StockTest {
    @Test
    fun `재고를 생성할 수 있다`() {
        // given
        val productId = 1L
        val quantity = 100

        // when
        val stock = Stock(productId = productId, quantity = quantity)

        // then
        assertThat(stock.productId).isEqualTo(productId)
        assertThat(stock.quantity).isEqualTo(quantity)
    }

    @Test
    fun `재고가 0 미만이면 예외가 발생한다`() {
        assertThatThrownBy {
            Stock(productId = 1L, quantity = -1)
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("재고")
    }

    @Test
    fun `재고를 감소시킬 수 있다`() {
        // given
        val stock = Stock(productId = 1L, quantity = 100)

        // when
        stock.decrease(30)

        // then
        assertThat(stock.quantity).isEqualTo(70)
    }

    @Test
    fun `재고보다 많이 감소시키면 예외가 발생한다`() {
        // given
        val stock = Stock(productId = 1L, quantity = 100)

        // when & then
        assertThatThrownBy {
            stock.decrease(101)
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("재고 부족")
    }

    @Test
    fun `재고를 증가시킬 수 있다`() {
        // given
        val stock = Stock(productId = 1L, quantity = 100)

        // when
        stock.increase(50)

        // then
        assertThat(stock.quantity).isEqualTo(150)
    }

    @Test
    fun `재고 사용 가능 여부를 확인할 수 있다`() {
        // given
        val stock = Stock(productId = 1L, quantity = 100)

        // when & then
        assertThat(stock.isAvailable(50)).isTrue()
        assertThat(stock.isAvailable(100)).isTrue()
        assertThat(stock.isAvailable(101)).isFalse()
    }
}
