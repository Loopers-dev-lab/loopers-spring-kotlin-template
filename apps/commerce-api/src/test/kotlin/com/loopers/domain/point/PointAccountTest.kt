package com.loopers.domain.point

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test

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

    @DisplayName("포인트 차감 테스트")
    @Nested
    inner class Deduct {

        @DisplayName("유효한 금액으로 차감하면 잔액이 감소한다")
        @Test
        fun `decrease balance when deduct with valid amount`() {
            // given
            val initialBalance = Money.krw(10000)
            val pointAccount = createPointAccount(balance = initialBalance)
            val deductAmount = Money.krw(3000)

            // when
            pointAccount.deduct(deductAmount)

            // then
            assertThat(pointAccount.balance).isEqualTo(Money.krw(7000))
        }

        @DisplayName("잔액 전액을 차감하면 잔액이 0원이 된다")
        @Test
        fun `balance becomes zero when deduct all balance`() {
            // given
            val initialBalance = Money.krw(10000)
            val pointAccount = createPointAccount(balance = initialBalance)

            // when
            pointAccount.deduct(initialBalance)

            // then
            assertThat(pointAccount.balance).isEqualTo(Money.ZERO_KRW)
        }

        @DisplayName("0원 차감 시도 시 예외가 발생한다")
        @Test
        fun `throws exception when deduct with zero amount`() {
            // given
            val pointAccount = createPointAccount(balance = Money.krw(10000))
            val zeroAmount = Money.ZERO_KRW

            // when
            val exception = assertThrows<CoreException> {
                pointAccount.deduct(zeroAmount)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("차감은 양수여야 합니다.")
        }

        @DisplayName("음수 금액 차감 시도 시 예외가 발생한다")
        @Test
        fun `throws exception when deduct with negative amount`() {
            // given
            val pointAccount = createPointAccount(balance = Money.krw(10000))
            val negativeAmount = Money.krw(-1000)

            // when
            val exception = assertThrows<CoreException> {
                pointAccount.deduct(negativeAmount)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("차감은 양수여야 합니다.")
        }

        @DisplayName("잔액보다 많은 금액 차감 시도 시 예외가 발생한다")
        @Test
        fun `throws exception when deduct amount exceeds balance`() {
            // given
            val initialBalance = Money.krw(5000)
            val pointAccount = createPointAccount(balance = initialBalance)
            val excessAmount = Money.krw(10000)

            // when
            val exception = assertThrows<CoreException> {
                pointAccount.deduct(excessAmount)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("포인트가 부족합니다.")
        }
    }

    fun createPointAccount(
        userId: Long = 1L,
        balance: Money = Money.krw(1000),
    ): PointAccount {
        return PointAccount.of(userId, balance)
    }
}
