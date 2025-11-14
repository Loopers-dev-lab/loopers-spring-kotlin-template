package com.loopers.domain.stock

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StockTest {

    @DisplayName("차감 후 재고가 음수가 되면, OUT_OF_STOCK 예외가 발생한다.")
    @Test
    fun throwsOutOfStockException_whenStockBecomesNegativeAfterDeduction() {
        // arrange
        val stock = Stock.of(productId = 1L, quantity = 50)

        // act
        val exception = assertThrows<CoreException> {
            stock.deduct(51)
        }

        // assert
        assertThat(exception.errorType).isEqualTo(ErrorType.OUT_OF_STOCK)
    }
}
