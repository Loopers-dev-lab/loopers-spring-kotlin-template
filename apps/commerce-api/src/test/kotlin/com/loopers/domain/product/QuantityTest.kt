package com.loopers.domain.product

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class QuantityTest {

    @DisplayName("수량을 생성할 수 있다")
    @Test
    fun createQuantity() {
        val quantity = Quantity.of(5)

        assertThat(quantity.value).isEqualTo(5)
    }

    @DisplayName("0 이하의 수량으로 생성 시 예외가 발생한다")
    @ParameterizedTest
    @ValueSource(ints = [0, -5, -100])
    fun failToCreateWithNegativeQuantity(value: Int) {
        val exception = assertThrows<CoreException> {
            Quantity.of(value)
        }
        assertThat(exception.errorType).isEqualTo(ErrorType.INVALID_QUANTITY)
    }
}
