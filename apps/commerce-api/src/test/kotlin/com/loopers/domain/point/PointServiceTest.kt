package com.loopers.domain.point

import com.loopers.IntegrationTest
import com.loopers.domain.user.Gender
import com.loopers.domain.user.User
import com.loopers.domain.user.UserCommand
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import java.util.stream.Stream

class PointServiceTest : IntegrationTest() {

    @Autowired
    private lateinit var userJpaRepository: UserJpaRepository

    @Autowired
    private lateinit var pointService: PointService

    @DisplayName("PointService Charge")
    @Nested
    inner class Charge {
        @Test
        fun `포인트 충전에 성공한다`() {
            // given
            val user = userJpaRepository.save(User.singUp(createSignUpCommand()))
            val userId = 1L

            // when
            pointService.charge(
                amount = 1000L,
                userId = userId,
            )
            val secondPoint = pointService.charge(
                amount = 2000L,
                userId = userId,
            )

            // then
            assertSoftly { softly ->
                softly.assertThat(secondPoint).isNotNull()
                softly.assertThat(secondPoint.userId).isEqualTo(user.id)
                softly.assertThat(secondPoint.amount).isEqualTo(Amount(3000L))
            }
        }
    }

    @DisplayName("PointService Use")
    @Nested
    inner class Use {
        @Test
        fun `포인트가 충분할 때 사용에 성공한다`() {
            // given
            val user = userJpaRepository.save(User.singUp(createSignUpCommand()))
            pointService.charge(amount = 5000L, userId = user.id)

            // when
            pointService.use(userId = user.id, amount = 2000L)

            // then
            val point = pointService.getBy(user.id)
            assertThat(point!!.amount.value).isEqualTo(3000L)
        }

        @ParameterizedTest(name = "충전 {0}원, 사용 {1}원 → 잔액 {2}원")
        @MethodSource("com.loopers.domain.point.PointServiceTest#useAmountProvider")
        fun `다양한 금액으로 사용 시 정확히 차감된다`(chargeAmount: Long, useAmount: Long, expectedBalance: Long) {
            // given
            val user = userJpaRepository.save(User.singUp(createSignUpCommand()))
            pointService.charge(amount = chargeAmount, userId = user.id)

            // when
            pointService.use(userId = user.id, amount = useAmount)

            // then
            val point = pointService.getBy(user.id)
            assertThat(point!!.amount.value).isEqualTo(expectedBalance)
        }

        @Test
        fun `여러 번 사용하여 잔액이 누적 차감된다`() {
            // given
            val user = userJpaRepository.save(User.singUp(createSignUpCommand()))
            pointService.charge(amount = 10000L, userId = user.id)

            // when
            pointService.use(userId = user.id, amount = 2000L)
            pointService.use(userId = user.id, amount = 3000L)
            pointService.use(userId = user.id, amount = 1000L)

            // then
            val point = pointService.getBy(user.id)
            assertThat(point!!.amount.value).isEqualTo(4000L)
        }

        @Test
        fun `포인트가 부족하면 사용에 실패한다`() {
            // given
            val user = userJpaRepository.save(User.singUp(createSignUpCommand()))
            pointService.charge(amount = 1000L, userId = user.id)

            // when & then
            assertThatThrownBy {
                pointService.use(userId = user.id, amount = 2000L)
            }.isInstanceOf(CoreException::class.java)
        }
    }

    companion object {
        @JvmStatic
        fun useAmountProvider() = Stream.of(
            Arguments.of(5000L, 5000L, 0L),
            Arguments.of(10000L, 10000L, 0L),
            Arguments.of(50000L, 50000L, 0L),
            Arguments.of(10000L, 3000L, 7000L),
            Arguments.of(5000L, 1000L, 4000L),
        )
    }

    @DisplayName("PointService Get")
    @Nested
    inner class Get {
        @Test
        fun `해당 ID 의 회원이 존재할 경우, 보유 포인트가 반환된다`() {
            // given
            val user = userJpaRepository.save(User.singUp(createSignUpCommand()))
            pointService.charge(
                amount = 1000L,
                userId = user.id,
            )

            // when
            val point = pointService.getBy(user.id)

            // then
            assertSoftly { softly ->
                softly.assertThat(point).isNotNull()
                softly.assertThat(point!!.userId).isEqualTo(user.id)
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
}
