package com.loopers.domain.shared

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MoneyTest {

    @DisplayName("금액을 생성할 수 있다")
    @Test
    fun createMoney() {
        val money = Money.of(1000L)

        assertThat(money.amount).isEqualTo(1000L)
    }

    @DisplayName("0원 금액을 생성할 수 있다")
    @Test
    fun createZeroMoney() {
        val money = Money.zero()

        assertThat(money.amount).isEqualTo(0L)
    }

    @DisplayName("음수 금액으로 생성 시 예외가 발생한다")
    @Test
    fun failToCreateWithNegativeAmount() {
        val exception = assertThrows<CoreException> {
            Money.of(-1000L)
        }

        assertThat(exception.errorType).isEqualTo(ErrorType.INVALID_POINT_AMOUNT)
    }

    @DisplayName("금액을 더할 수 있다")
    @Test
    fun addMoney() {
        val money1 = Money.of(1000L)
        val money2 = Money.of(2000L)

        val result = money1.plus(money2)

        assertThat(result.amount).isEqualTo(3000L)
    }

}
