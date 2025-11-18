package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class StockTest {

    @DisplayName("재고를 생성할 수 있다")
    @Test
    fun createStock() {
        val stock = Stock.of(100)

        assertThat(stock.quantity).isEqualTo(100)
    }

    @DisplayName("0개 재고를 생성할 수 있다")
    @Test
    fun createZeroStock() {
        val stock = Stock.zero()

        assertThat(stock.quantity).isEqualTo(0)
    }

    @DisplayName("음수 재고로 생성 시 예외가 발생한다")
    @Test
    fun failToCreateWithNegativeQuantity() {
        val exception = assertThrows<CoreException> {
            Stock.of(-10)
        }

        assertThat(exception.errorType).isEqualTo(ErrorType.INVALID_STOCK)
    }

    @DisplayName("재고를 감소시킬 수 있다")
    @Test
    fun decreaseStock() {
        val stock = Stock.of(100)

        val result = stock.decrease(50)

        assertThat(result.quantity).isEqualTo(50)
    }

    @DisplayName("재고가 부족할 경우 감소 시 예외가 발생한다")
    @Test
    fun failToDecreaseWhenStockIsInsufficient() {
        val stock = Stock.of(10)

        val exception = assertThrows<CoreException> {
            stock.decrease(20)
        }

        assertThat(exception.errorType).isEqualTo(ErrorType.INSUFFICIENT_STOCK)
    }

    @DisplayName("0 이하의 수량으로 감소 시 예외가 발생한다")
    @ParameterizedTest
    @ValueSource(ints = [0, -3, -105])
    fun failToDecreaseWhenStockIsZeroOrNegative(value: Int) {
        val stock = Stock.of(100)

        val exception = assertThrows<CoreException> {
            stock.decrease(value)
        }

        assertThat(exception.errorType).isEqualTo(ErrorType.INVALID_STOCK)
    }


    @DisplayName("재고가 충분한지 확인할 수 있다")
    @Test
    fun checkHasEnoughStock() {
        val stock = Stock.of(100)

        assertThat(stock.hasEnough(50)).isTrue()
        assertThat(stock.hasEnough(101)).isFalse()
        assertThat(stock.hasEnough(100)).isTrue()
    }





}
