package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class StockTest {

    @Test
    fun `create 메서드로 Stock 객체를 생성할 수 있다`() {
        // given
        val productId = 1L
        val quantity = 10L

        // when
        val productLike = Stock.create(
            quantity = quantity,
            productId = productId,
        )

        // then
        assertSoftly { softly ->
            softly.assertThat(productLike.productId).isEqualTo(1L)
        }
    }

    @Test
    fun `재고를 정상적으로 감소시킨다`() {
        // given
        val stock = Stock.create(quantity = 10L, productId = 1L)

        // when
        stock.decrease(5L)

        // then
        assertThat(stock.quantity).isEqualTo(5L)
    }

    @Test
    fun `재고를 전부 소진할 수 있다`() {
        // given
        val stock = Stock.create(quantity = 10L, productId = 1L)

        // when
        stock.decrease(10L)

        // then
        assertThat(stock.quantity).isEqualTo(0L)
    }

    @Test
    fun `재고가 0일 때 감소 시도 시 예외가 발생한다`() {
        // given
        val stock = Stock.create(quantity = 0L, productId = 1L)

        // when & then
        assertThatThrownBy { stock.decrease(1L) }
            .isInstanceOf(CoreException::class.java)
            .hasFieldOrPropertyWithValue("errorType", ErrorType.INSUFFICIENT_STOCK)
    }

    @Test
    fun `재고보다 많은 수량 감소 시 예외가 발생한다`() {
        // given
        val stock = Stock.create(quantity = 5L, productId = 1L)

        // when & then
        assertThatThrownBy { stock.decrease(10L) }
            .isInstanceOf(CoreException::class.java)
            .hasFieldOrPropertyWithValue("errorType", ErrorType.INSUFFICIENT_STOCK)
    }

    @ParameterizedTest
    @ValueSource(longs = [0L, -1L, -10L, -100L])
    fun `0 이하의 수량으로 감소 시도 시 예외가 발생한다`(amount: Long) {
        // given
        val stock = Stock.create(quantity = 10L, productId = 1L)

        // when & then
        assertThatThrownBy {
            stock.decrease(amount)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("차감 수량은 0보다 커야 합니다")
    }

    @Test
    fun `여러 번 재고를 감소시킬 수 있다`() {
        // given
        val stock = Stock.create(quantity = 100L, productId = 1L)

        // when
        stock.decrease(30L)
        stock.decrease(20L)
        stock.decrease(10L)

        // then
        assertThat(stock.quantity).isEqualTo(40L)
    }
}
