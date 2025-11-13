package com.loopers.domain.point

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PointAccountTest {
    @DisplayName("충전 테스트")
    @Nested
    inner class Charge {

        @DisplayName("양수로 포인트를 충전할 수 있다.")
        @ParameterizedTest
        @ValueSource(ints = [1, 10, 100, 1000, 10000])
        fun chargePoint_whenAmountIsPositive(amount: Int) {
            // given
            val currentBalance = Money.krw(2312312)
            val pointAccount = createPointAccount(
                balance = currentBalance,
            )

            // when
            val chargeAmount = Money.krw(amount)
            pointAccount.charge(chargeAmount)

            // then
            assertEquals(chargeAmount.plus(currentBalance), pointAccount.balance)
        }

        @DisplayName("0이하의 정수로 포인트를 충전할 수 없다.")
        @ParameterizedTest
        @ValueSource(ints = [-2312, -1, 0])
        fun throwsException_whenAmountIsZeroOrBelow(amount: Int) {
            // given
            val pointAccount = createPointAccount()

            // when
            val chargeAmount = Money.krw(amount)
            val exception = assertThrows<CoreException> {
                pointAccount.charge(chargeAmount)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("충전은 양수여야 합니다.")
        }
    }

    fun createPointAccount(
        userId: Long = 1L,
        balance: Money = Money.krw(1000),
    ): PointAccount {
        return PointAccount.of(userId, balance)
    }
}
