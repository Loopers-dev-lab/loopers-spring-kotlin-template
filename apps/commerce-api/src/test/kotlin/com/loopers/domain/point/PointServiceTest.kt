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

            val pointModel = PointFixture.create(userId = user.id, balance = 100L)
            pointRepository.save(pointModel)

            // act
            val findPoint = pointService.getPointByUserId(user.id)

            // assert
            assertThat(findPoint?.balance).isEqualTo(100L)
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

            val pointModel = PointFixture.create(userId = user.id, balance = 100L)
            pointRepository.save(pointModel)

            // act
            val chargedPoint = pointService.charge(user.id, 10L)
            assertThat(chargedPoint.balance).isEqualTo(110L)
        }

        @DisplayName("존재하지 않는 유저 ID 로 충전을 시도한 경우, 실패한다.")
        @Test
        fun chargePoint_whenNotExistsUser() {
            // arrange
            val notExistUserId = 1L

            // act, assert
            val exception = assertThrows<CoreException> {
                pointService.charge(notExistUserId, 10L)
            }

            assertThat(exception.message).isEqualTo("존재하지 않는 유저입니다.")
        }

        @DisplayName("존재하는 유저 ID 로 충전량을 음수로 충전할 경우, 실패한다")
        @Test
        fun chargePointFails_whenAmountIsNotPositive() {
            // arrange
            val user = UserFixture.create()
            userRepository.save(user)

            val pointModel = PointFixture.create(userId = user.id, balance = 100L)
            pointRepository.save(pointModel)

            // act
            val exception = assertThrows<IllegalArgumentException> {
                pointService.charge(user.id, -10L)
            }
            assertThat(exception.message).isEqualTo("충전 금액은 0보다 커야 합니다.")
        }
    }
}
