package com.loopers.domain.point

import com.loopers.IntegrationTestSupport
import com.loopers.domain.user.UserFixture
import com.loopers.infrastructure.point.PointJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class PointServiceTest(
    private val pointService: PointService,
    private val userRepository: UserJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val pointRepository: PointJpaRepository,
) : IntegrationTestSupport() {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("포인트 조회 시")
    @Nested
    inner class GetPoint {
        @DisplayName("해당 ID 의 회원이 존재할 경우, 보유 포인트가 반환된다.")
        @Test
        fun returnPointBalance_whenUserExists() {
            // arrange
            val user = UserFixture.create(gender = "FEMALE")
            userRepository.save(user)

            val pointModel =
                PointFixture.create(userId = user.id, balance = BigDecimal.valueOf(100))
            pointRepository.save(pointModel)

            // act
            val findPoint = pointService.getPointByUserId(user.id)

            // assert
            assertThat(findPoint?.balance?.amount).isEqualByComparingTo(BigDecimal.valueOf(100))
        }

        @DisplayName("해당 ID 의 회원이 존재하지 않을 경우, null 이 반환된다.")
        @Test
        fun returnNull_whenUserIsNotExists() {
            // arrange
            val notExistUserId = 1L

            // act
            val pointModel = pointService.getPointByUserId(notExistUserId)

            // assert
            assertThat(pointModel).isNull()
        }
    }

    @DisplayName("포인트 충전 시")
    @Nested
    inner class Charge {
        @DisplayName("존재하는 유저 ID 로 충전을 시도한 경우, 성공한다.")
        @Test
        fun chargePoint_whenExistsUser() {
            // arrange
            val user = UserFixture.create()
            userRepository.save(user)

            val pointModel =
                PointFixture.create(userId = user.id, balance = BigDecimal.valueOf(100))
            pointRepository.save(pointModel)

            // act
            val chargedPoint = pointService.charge(user.id, BigDecimal.valueOf(10))
            assertThat(chargedPoint.balance.amount).isEqualByComparingTo(BigDecimal.valueOf(110))
        }

        @DisplayName("존재하지 않는 유저 ID 로 충전을 시도한 경우, 실패한다.")
        @Test
        fun chargePoint_whenNotExistsUser() {
            // arrange
            val notExistUserId = 1L

            // act, assert
            val exception =
                assertThrows<CoreException> {
                    pointService.charge(notExistUserId, BigDecimal.valueOf(10))
                }

            assertThat(exception.message).isEqualTo("유저가 존재하지 않습니다.")
        }

        @DisplayName("존재하는 유저 ID 로 충전량을 0원으로 충전할 경우, 실패한다")
        @Test
        fun chargePointFails_whenAmountIsZero() {
            // arrange
            val user = UserFixture.create()
            userRepository.save(user)

            val pointModel =
                PointFixture.create(userId = user.id, balance = BigDecimal.valueOf(100))
            pointRepository.save(pointModel)

            // act
            val exception =
                assertThrows<IllegalArgumentException> {
                    pointService.charge(user.id, BigDecimal.ZERO)
                }
            assertThat(exception.message).isEqualTo("충전 금액은 0보다 커야 합니다.")
        }
    }

    @DisplayName("포인트 결제 시")
    @Nested
    inner class Pay {
        @DisplayName("존재하는 유저 ID로 결제를 시도하고 잔액이 충분한 경우, 성공한다.")
        @Test
        fun paySuccess_whenExistsUserAndSufficientBalance() {
            // arrange
            val user = UserFixture.create()
            userRepository.save(user)

            val pointModel =
                PointFixture.create(userId = user.id, balance = BigDecimal.valueOf(1000))
            pointRepository.save(pointModel)

            // act
            val paidPoint = pointService.pay(user.id, BigDecimal.valueOf(300))

            // assert
            assertThat(paidPoint.balance.amount).isEqualByComparingTo(BigDecimal.valueOf(700))
        }

        @DisplayName("존재하지 않는 유저 ID로 결제를 시도한 경우, 실패한다.")
        @Test
        fun payFails_whenNotExistsUser() {
            // arrange
            val notExistUserId = 1L

            // act, assert
            val exception =
                assertThrows<CoreException> {
                    pointService.pay(notExistUserId, BigDecimal.valueOf(100))
                }

            assertThat(exception.message).isEqualTo("유저가 존재하지 않습니다.")
        }

        @DisplayName("결제 금액이 0원인 경우, 실패한다.")
        @Test
        fun payFails_whenAmountIsZero() {
            // arrange
            val user = UserFixture.create()
            userRepository.save(user)

            val pointModel =
                PointFixture.create(userId = user.id, balance = BigDecimal.valueOf(1000))
            pointRepository.save(pointModel)

            // act, assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    pointService.pay(user.id, BigDecimal.ZERO)
                }

            assertThat(exception.message).isEqualTo("사용 금액은 0보다 커야 합니다.")
        }

        @DisplayName("잔액이 부족한 경우, 실패한다.")
        @Test
        fun payFails_whenInsufficientBalance() {
            // arrange
            val user = UserFixture.create()
            userRepository.save(user)

            val pointModel =
                PointFixture.create(userId = user.id, balance = BigDecimal.valueOf(100))
            pointRepository.save(pointModel)

            // act, assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    pointService.pay(user.id, BigDecimal.valueOf(500))
                }

            assertThat(exception.message).isEqualTo("잔액이 부족합니다.")
        }

        @DisplayName("결제 금액이 음수인 경우, 실패한다.")
        @Test
        fun payFails_whenAmountIsNegative() {
            // arrange
            val user = UserFixture.create()
            userRepository.save(user)

            val pointModel =
                PointFixture.create(userId = user.id, balance = BigDecimal.valueOf(1000))
            pointRepository.save(pointModel)

            // act, assert
            val exception =
                assertThrows<IllegalArgumentException> {
                    pointService.pay(user.id, BigDecimal.valueOf(-100))
                }

            assertThat(exception.message).isEqualTo("금액은 0 이상이어야 합니다.")
        }
    }
}
