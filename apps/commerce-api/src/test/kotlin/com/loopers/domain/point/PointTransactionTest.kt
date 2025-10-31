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

class PointTransactionTest {

    @DisplayName("충전 테스트")
    @Nested
    inner class Charge {
        val correctUserId = 1L

        @DisplayName("양수로 포인트를 충전할 수 있다.")
        @ParameterizedTest
        @ValueSource(ints = [1, 10, 100, 1000, 10000])
        fun chargePoint_whenAmountIsPositive(amount: Int) {
            // when
            val chargeAmount = Money.krw(amount)
            val transaction = PointTransaction.charge(correctUserId, chargeAmount)

            // then
            assertEquals(correctUserId, transaction.userId)
            assertEquals(chargeAmount, transaction.amount)
            assertEquals(PointTransactionType.CHARGE, transaction.transactionType)
        }

        @DisplayName("0이하의 정수로 포인트를 충전할 수 없다.")
        @ParameterizedTest
        @ValueSource(ints = [-2312, -1, 0])
        fun throwsException_whenAmountIsZeroOrBelow(amount: Int) {
            // when
            val exception = assertThrows<CoreException> {
                PointTransaction.charge(correctUserId, Money.krw(amount))
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("충전은 양수여야 합니다.")
        }
    }
}
