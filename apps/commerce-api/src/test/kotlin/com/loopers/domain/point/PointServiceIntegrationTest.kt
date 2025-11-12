package com.loopers.domain.point

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
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
        @DisplayName("포인트 계좌를 생성할 수 있다.")
        @Test
        fun createPointAccount_whenValidUserIdIsProvided() {
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
        @DisplayName("존재하지 않는 유저 id로 충전을 할 수 없다.")
        @Test
        fun throwsException_whenInvalidUserIdIsProvided() {
            // when
            val invalidUserId = 999L
            val exception = assertThrows<CoreException> {
                charge(invalidUserId)
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
            assertThat(exception.message).isEqualTo("[id = $invalidUserId] 유저를 찾을 수 없습니다.")
        }

        @DisplayName("존재하는 유저 id로 포인트를 충전할 수 있다.")
        @Test
        fun chargePoint_whenValidUserIdIsProvided() {
            // given
            val currentBalance = Money.krw(2312312)
            val createPointAccount = createPointAccount(
                balance = currentBalance,
            )

            // when
            val chargeAmount = Money.krw(1000)
            charge(
                userId = createPointAccount.userId,
                amount = chargeAmount,
            )

            // then
            val pointAccount = pointAccountRepository.findByUserId(createPointAccount.userId)
            assertAll(
                { assertThat(pointAccount?.balance).isEqualTo(currentBalance.plus(chargeAmount)) },
            )
        }
    }

    private fun charge(
        userId: Long = 1L,
        amount: Money = Money.krw(1000),
    ): PointAccount = pointService.charge(
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
