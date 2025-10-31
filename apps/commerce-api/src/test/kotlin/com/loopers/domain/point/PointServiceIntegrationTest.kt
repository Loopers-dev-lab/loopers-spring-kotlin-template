package com.loopers.domain.point

import com.loopers.domain.user.User
import com.loopers.infrastructure.point.PointJpaRepository
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class PointServiceIntegrationTest @Autowired constructor(
    private val pointJpaRepository: PointJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val pointService: PointService,
) {

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("포인트 조회 시")
    @Nested
    inner class GetPoint {

        @DisplayName("해당 ID의 회원이 존재할 경우, 보유 포인트가 반환된다.")
        @Test
        fun returnsPoint_whenUserExists() {
            // arrange
            val testPoint = 1000L

            val user = userJpaRepository.save(
                User(
                    username = "testuser",
                    email = "test@example.com",
                    birthDate = "1997-03-25",
                    gender = User.Gender.MALE,
                ),
            )
            pointJpaRepository.save(
                Point.of(
                    userId = user.id,
                    initialAmount = testPoint,
                ),
            )

            // act
            val result = pointService.findByUserId(user.id)

            // assert
            assertThat(result).isNotNull()
            assertThat(result?.userId).isEqualTo(user.id)
            assertThat(result?.amount).isEqualTo(testPoint)
        }

        @DisplayName("해당 ID의 회원이 존재하지 않을 경우, null이 반환된다.")
        @Test
        fun returnsNull_whenUserDoesNotExist() {
            // arrange
            val userId = 135L

            // act
            val result = pointService.findByUserId(userId)

            // assert
            assertThat(result).isNull()
        }
    }

    @DisplayName("포인트 충전 시")
    @Nested
    inner class ChargePoint {

        @DisplayName("존재하지 않는 유저 ID로 충전을 시도한 경우, 404 NOT_FOUND 에러가 발생한다.")
        @Test
        fun throwsException_whenUserDoesNotExist() {
            // arrange
            val userId = 999L
            val amount = 1000L

            // act
            val exception = assertThrows<CoreException> {
                pointService.chargePoint(userId, amount)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }
}
