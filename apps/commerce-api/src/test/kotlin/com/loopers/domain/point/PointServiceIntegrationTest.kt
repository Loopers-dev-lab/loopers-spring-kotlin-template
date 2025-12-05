package com.loopers.domain.point

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class PointServiceIntegrationTest @Autowired constructor(
    private val pointService: PointService,
    private val pointAccountRepository: PointAccountRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("포인트 계좌 생성 통합테스트")
    @Nested
    inner class Create {
        @DisplayName("포인트 계좌를 생성할 수 있다")
        @Test
        fun `create point account when valid user id is provided`() {
            // when
            val userId = 1L
            val pointAccount = pointService.createPointAccount(userId)

            // then
            assertAll(
                { assertThat(pointAccount.userId).isEqualTo(userId) },
                { assertThat(pointAccount.balance).isEqualTo(Money.ZERO_KRW) },
            )
        }
    }

    @DisplayName("포인트 충전 통합테스트")
    @Nested
    inner class Charge {
        @DisplayName("존재하지 않는 유저 id로 충전할 수 없다")
        @Test
        fun `throw exception when invalid user id is provided`() {
            // when
            val invalidUserId = 999L
            val exception = assertThrows<CoreException> {
                charge(invalidUserId)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
            assertThat(exception.message).isEqualTo("[id = $invalidUserId] 유저를 찾을 수 없습니다.")
        }

        @DisplayName("존재하는 유저 id로 포인트를 충전할 수 있다")
        @Test
        fun `charge point when valid user id is provided`() {
            // given
            val currentBalance = Money.krw(2312312)
            val pointAccount = createPointAccount(balance = currentBalance)

            // when
            val chargeAmount = Money.krw(1000)
            charge(
                userId = pointAccount.userId,
                amount = chargeAmount,
            )

            // then
            val updatedPointAccount = pointAccountRepository.findByUserId(pointAccount.userId)
            assertThat(updatedPointAccount?.balance).isEqualTo(currentBalance.plus(chargeAmount))
        }
    }

    @DisplayName("포인트 차감 통합테스트")
    @Nested
    inner class Deduct {
        @DisplayName("존재하지 않는 유저 id로 차감할 수 없다")
        @Test
        fun `throw exception when invalid user id is provided`() {
            // when
            val invalidUserId = 999L
            val exception = assertThrows<CoreException> {
                deduct(invalidUserId)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
            assertThat(exception.message).isEqualTo("[id = $invalidUserId] 유저를 찾을 수 없습니다.")
        }

        @DisplayName("잔액이 부족하면 차감할 수 없다")
        @Test
        fun `throw exception when balance is insufficient`() {
            // given
            val pointAccount = createPointAccount(balance = Money.krw(5000))

            // when
            val exception = assertThrows<CoreException> {
                deduct(
                    userId = pointAccount.userId,
                    amount = Money.krw(10000),
                )
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("포인트를 차감할 수 있다")
        @Test
        fun `deduct point when balance is sufficient`() {
            // given
            val currentBalance = Money.krw(10000)
            val pointAccount = createPointAccount(balance = currentBalance)

            // when
            val deductAmount = Money.krw(3000)
            deduct(
                userId = pointAccount.userId,
                amount = deductAmount,
            )

            // then
            val updatedPointAccount = pointAccountRepository.findByUserId(pointAccount.userId)
            assertThat(updatedPointAccount?.balance).isEqualTo(currentBalance.minus(deductAmount))
        }

        @DisplayName("여러 번 차감해도 정상적으로 동작한다")
        @Test
        fun `deduct point multiple times`() {
            // given
            val initialBalance = Money.krw(10000)
            val pointAccount = createPointAccount(balance = initialBalance)

            // when
            deduct(userId = pointAccount.userId, amount = Money.krw(2000))
            deduct(userId = pointAccount.userId, amount = Money.krw(3000))
            deduct(userId = pointAccount.userId, amount = Money.krw(1000))

            // then
            val updatedPointAccount = pointAccountRepository.findByUserId(pointAccount.userId)
            assertThat(updatedPointAccount?.balance).isEqualTo(Money.krw(4000))
        }
    }

    @DisplayName("포인트 복구 통합테스트")
    @Nested
    inner class Restore {
        @DisplayName("존재하지 않는 유저 id로 복구할 수 없다")
        @Test
        fun `throw exception when invalid user id is provided`() {
            // when
            val invalidUserId = 999L
            val exception = assertThrows<CoreException> {
                pointService.restore(invalidUserId, Money.krw(1000))
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("포인트를 복구할 수 있다")
        @Test
        fun `restore point when valid user id is provided`() {
            // given
            val initialBalance = Money.krw(5000)
            val pointAccount = createPointAccount(balance = initialBalance)

            // when
            val restoreAmount = Money.krw(3000)
            pointService.restore(pointAccount.userId, restoreAmount)

            // then
            val updatedPointAccount = pointAccountRepository.findByUserId(pointAccount.userId)
            assertThat(updatedPointAccount?.balance).isEqualTo(initialBalance.plus(restoreAmount))
        }
    }

    private fun charge(
        userId: Long = 1L,
        amount: Money = Money.krw(1000),
    ): PointAccount = pointService.charge(
        userId = userId,
        amount = amount,
    )

    private fun deduct(
        userId: Long = 1L,
        amount: Money = Money.krw(1000),
    ): PointAccount = pointService.deduct(
        userId = userId,
        amount = amount,
    )

    private fun createPointAccount(
        userId: Long = 1L,
        balance: Money = Money.ZERO_KRW,
    ): PointAccount {
        val account = PointAccount.of(userId, balance)
        return pointAccountRepository.save(account)
    }
}
