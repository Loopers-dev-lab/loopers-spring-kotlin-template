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

    @DisplayName("재고 추가 테스트")
    @Nested
    inner class Increase {
        @DisplayName("재고를 1 이상으로 증가시키면 재고가 증가한다.")
        @ParameterizedTest
        @ValueSource(ints = [1, 3, 10])
        fun `increase stock when valid amount is provided`(amount: Int) {
            // given
            val stock = createStock()

            // when
            val increasedStock = stock.increase(amount)

            // then
            assertThat(increasedStock.amount).isEqualTo(stock.amount + amount)
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
        // when 재고를 감소시키면
        // then 재고가 1씩 감소한다.
        @Test
        fun `decrease stock when valid amount is provided`() {
            // given
            val existAmount = 10
            val stock = createStock(
                amount = existAmount,
            )

            // when
            val decreaseAmount = 5
            val decreasedStock = stock.decrease(decreaseAmount)

            // then
            assertThat(decreasedStock.amount).isEqualTo(stock.amount - decreaseAmount)
        }

        // when 0보다 작은 값으로 재고를 감소시키면
        // then 예외가 발생한다.
        @Test
        fun `throws exception when decrease amount is zero or below`() {
            // given
            val stock = createStock()

            // when
            val negativeAmount = -1
            val exception = assertThrows<CoreException> {
                stock.decrease(negativeAmount)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("재고 감소량은 0보다 커야 합니다.")
        }

        // when 현재 재고보다 적은 값으로 재고를 감소시키면
        // then 예외가 발생한다.
        @Test
        fun `throws exception when decrease amount is greater than exist amount`() {
            // given
            val existAmount = 10
            val stock = createStock(
                amount = existAmount,
            )

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

    fun createStock(
        amount: Int = 10,
    ): Stock {
        return Stock.of(amount)
    }
}
