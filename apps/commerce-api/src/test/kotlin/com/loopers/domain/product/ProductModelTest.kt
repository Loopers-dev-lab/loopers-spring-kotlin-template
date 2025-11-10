package com.loopers.domain.product

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProductModelTest {

    @DisplayName("재고 감소")
    @Nested
    inner class DecreaseStock {

        @DisplayName("요청한 수량만큼 재고가 감소한다")
        @Test
        fun decreaseStockSuccess() {
            val product = ProductModel.create(
                name = "Test product",
                stock = 100,
                refBrandId = 1,
            )

            product.decreaseStock(1)
            assertThat(product.stock).isEqualTo(99)
        }

        @DisplayName("음수 수량으로 재고를 감소할 수 없다")
        @Test
        fun decreaseStockFails_whenQuantityIsLessThanZero() {
            val product = ProductModel.create(
                name = "Test product",
                stock = 100,
                refBrandId = 1,
            )

            val exception = assertThrows<IllegalArgumentException> {
                product.decreaseStock(-1)
            }
            assertThat(exception.message).isEqualTo("감소 수량은 0보다 커야 합니다.")
        }
    }
}
