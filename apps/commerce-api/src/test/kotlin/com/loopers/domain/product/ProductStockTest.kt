package com.loopers.domain.product

import com.loopers.domain.product.entity.ProductStock
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test

class ProductStockTest {
    @Test
    fun `재고를 정상 등록한다`() {
        // when
        val productStock = ProductStock.create(1L, 10)

        // then
        assertThat(productStock.productOptionId).isEqualTo(1L)
        assertThat(productStock.quantity.value).isEqualTo(10)
    }

    @Test
    fun `재고 수량이 음수이면 예외가 발생한다`() {
        // when & then
        assertThrows<CoreException> {
            ProductStock.create(1L, -1)
        }
    }

    @Test
    fun `재고를 정상적으로 차감한다`() {
        // given
        val productStock = ProductStock.create(1L, 10)

        // when
        productStock.deduct(3)

        // then
        assertThat(productStock.quantity.value).isEqualTo(7)
    }

    @Test
    fun `재고가 부족하면 예외가 발생한다`() {
        // given
        val productStock = ProductStock.create(1L, 5)

        // when & then
        val exception = assertThrows<CoreException> {
            productStock.deduct(10)
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.PRODUCT_STOCK_NOT_ENOUGH)
    }
}
