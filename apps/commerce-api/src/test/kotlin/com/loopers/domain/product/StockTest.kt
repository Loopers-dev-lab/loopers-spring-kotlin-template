package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test

class StockTest {

    @DisplayName("재고 생성 테스트")
    @Nested
    inner class Create {
        @DisplayName("productId와 quantity로 재고를 생성할 수 있다.")
        @Test
        fun `create stock with productId and quantity`() {
            // given
            val productId = 1L
            val quantity = 10

            // when
            val stock = Stock.create(productId, quantity)

            // then
            assertThat(stock.productId).isEqualTo(productId)
            assertThat(stock.quantity).isEqualTo(quantity)
        }

        @DisplayName("음수 quantity로 생성하면 예외가 발생한다.")
        @Test
        fun `throws exception when quantity is negative`() {
            // given
            val productId = 1L
            val quantity = -1

            // when & then
            val exception = assertThrows<CoreException> {
                Stock.create(productId, quantity)
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("재고는 0 이상이어야 합니다.")
        }
    }

    @DisplayName("재고 추가 테스트")
    @Nested
    inner class Increase {
        @DisplayName("재고를 1 이상으로 증가시키면 재고가 증가한다.")
        @ParameterizedTest
        @ValueSource(ints = [1, 3, 10])
        fun `increase stock when valid amount is provided`(amount: Int) {
            // given
            val initialQuantity = 10
            val stock = createStock(quantity = initialQuantity)

            // when
            stock.increase(amount)

            // then
            assertThat(stock.quantity).isEqualTo(initialQuantity + amount)
        }

        @DisplayName("0 이하로 재고를 증가시키면 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(ints = [0, -1, -5])
        fun `throws exception when increase amount is zero or below`(amount: Int) {
            // given
            val stock = createStock()

            // when
            val exception = assertThrows<CoreException> {
                stock.increase(amount)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("재고 증가량은 0보다 커야 합니다.")
        }
    }

    @DisplayName("재고 감소 테스트")
    @Nested
    inner class Decrease {

        @DisplayName("재고를 1 이상으로 감소시키면 재고가 감소한다.")
        @ParameterizedTest
        @ValueSource(ints = [1, 3, 10])
        fun `decrease stock when valid amount is provided`(decreaseAmount: Int) {
            // given
            val existQuantity = 10
            val stock = createStock(quantity = existQuantity)

            // when
            stock.decrease(decreaseAmount)

            // then
            assertThat(stock.quantity).isEqualTo(existQuantity - decreaseAmount)
        }

        @DisplayName("0 이하로 재고를 감소시키면 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(ints = [0, -1, -5])
        fun `throws exception when decrease amount is zero or below`(amount: Int) {
            // given
            val stock = createStock()

            // when
            val exception = assertThrows<CoreException> {
                stock.decrease(amount)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("재고 감소량은 0보다 커야 합니다.")
        }

        @DisplayName("재고가 부족하면 예외가 발생한다.")
        @Test
        fun `throws exception when decrease amount is greater than exist amount`() {
            // given
            val existQuantity = 10
            val stock = createStock(quantity = existQuantity)

            // when
            val decreaseAmount = 20
            val exception = assertThrows<CoreException> {
                stock.decrease(decreaseAmount)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("재고가 부족합니다.")
        }
    }

    private fun createStock(
        productId: Long = 1L,
        quantity: Int = 10,
    ): Stock {
        return Stock.create(productId, quantity)
    }
}
