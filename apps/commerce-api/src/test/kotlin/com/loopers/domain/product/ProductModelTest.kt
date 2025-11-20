package com.loopers.domain.product

import com.loopers.domain.product.stock.StockModel
import com.loopers.support.error.CoreException
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
            val stock = StockModel.create(
                refProductId = 1L,
                amount = 100,
            )

            stock.occupy(1)
            assertThat(stock.amount).isEqualTo(99)
        }

        @DisplayName("음수 수량으로 재고를 감소할 수 없다")
        @Test
        fun decreaseStockFails_whenQuantityIsLessThanZero() {
            val stock = StockModel.create(
                refProductId = 1L,
                amount = 100,
            )

            val exception = assertThrows<CoreException> {
                stock.occupy(-1)
            }
            assertThat(exception.message).isEqualTo("차감 수량은 0보다 커야 합니다.")
        }
    }
}
