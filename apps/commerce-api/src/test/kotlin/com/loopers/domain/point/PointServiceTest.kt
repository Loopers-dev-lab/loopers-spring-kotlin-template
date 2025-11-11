package com.loopers.domain.point

import com.loopers.IntegrationTest
import com.loopers.domain.user.Gender
import com.loopers.domain.user.User
import com.loopers.domain.user.UserCommand
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class PointServiceTest : IntegrationTest() {

    @Autowired
    private lateinit var userJpaRepository: UserJpaRepository

    @Autowired
    private lateinit var pointService: PointService

    @DisplayName("PointService Charge")
    @Nested
    inner class Charge {
        @Test
        fun `존재하지 않는 유저 ID 로 충전을 시도한 경우, 실패한다`() {
            // given
            val command = createPointChargeCommand()

            // when & then
            assertThatThrownBy {
                pointService.charge(command)
            }.isInstanceOfSatisfying(CoreException::class.java) { error ->
                assertThat(error.errorType).isEqualTo(ErrorType.NOT_FOUND)
            }
        }

        @Test
        fun `포인트 충전에 성공한다`() {
            // given
            val user = userJpaRepository.save(User.create(createSignUpCommand()))

            // when
            pointService.charge(
                createPointChargeCommand(
                    amount = 1000L,
                ),
            )
            val secondPoint = pointService.charge(
                createPointChargeCommand(
                    amount = 2000L,
                ),
            )

            // then
            assertSoftly { softly ->
                softly.assertThat(secondPoint).isNotNull()
                softly.assertThat(secondPoint.userId).isEqualTo(user.userId)
                softly.assertThat(secondPoint.amount).isEqualTo(Amount(3000L))
            }
        }
    }

    @DisplayName("PointService Get")
    @Nested
    inner class Get {
        @Test
        fun `해당 ID 의 회원이 존재할 경우, 보유 포인트가 반환된다`() {
            // given
            val user = userJpaRepository.save(User.create(createSignUpCommand()))
            pointService.charge(
                createPointChargeCommand(
                    amount = 1000L,
                    userId = user.userId.value,
                ),
            )

            // when
            val point = pointService.getBy(user.userId.value)

            // then
            assertSoftly { softly ->
                softly.assertThat(point).isNotNull()
                softly.assertThat(point!!.userId.value).isEqualTo(user.userId.value)
                softly.assertThat(point.amount.value).isEqualTo(1000L)
            }
        }
    }

    private fun createSignUpCommand(
        userId: String = "testUserId",
        email: String = "test@example.com",
        birthDate: String = "2000-01-01",
        gender: Gender = Gender.MALE,
    ) = UserCommand.SignUp(userId, email, birthDate, gender)

    private fun createPointChargeCommand(
        amount: Long = 100L,
        userId: String = "testUserId",
    ) = PointCommand.Charge(amount, userId)
}
