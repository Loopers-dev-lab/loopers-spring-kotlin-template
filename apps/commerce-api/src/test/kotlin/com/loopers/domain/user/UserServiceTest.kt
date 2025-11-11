package com.loopers.domain.user

import com.loopers.IntegrationTest
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean

class UserServiceTest : IntegrationTest() {

    @MockitoSpyBean
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userService: UserService

    @DisplayName("UserService SingUp")
    @Nested
    inner class SignUp {
        @Test
        fun `회원 가입시 User 저장이 수행된다`() {
            // given
            val command = createSignUpCommand()

            // when
            val user = userService.signUp(command)

            // then
            assertSoftly { softly ->
                softly.assertThat(user).isNotNull
                softly.assertThat(user.userId.value).isEqualTo(command.userId)
                softly.assertThat(user.email.value).isEqualTo(command.email)
                softly.assertThat(user.birthDate.value).isEqualTo(command.birthDate)
                softly.assertThat(user.gender).isEqualTo(command.gender)
            }
            verify(userRepository, times(1)).save(any())
            verify(userRepository, times(1)).findBy(any<String>())
        }

        @Test
        fun `이미 가입된 userId로 회원가입 시도시 실패한다`() {
            // given
            val command = createSignUpCommand()
            userService.signUp(command)

            // when & then
            assertThatThrownBy {
                userService.signUp(command)
            }.isInstanceOfSatisfying(CoreException::class.java) { error ->
                assertThat(error.errorType).isEqualTo(ErrorType.CONFLICT)
            }
        }
    }

    @DisplayName("UserService MyInfo")
    @Nested
    inner class MyInfo {
        @Test
        fun `해당 ID 의 회원이 존재할 경우, 회원 정보가 반환된다`() {
            // given
            val command = createSignUpCommand()
            val signUpUser = userService.signUp(command)

            // when
            val user = userService.getMyInfo(signUpUser.userId.value)

            // then
            assertSoftly { softly ->
                softly.assertThat(user).isNotNull
                softly.assertThat(user.userId.value).isEqualTo(command.userId)
                softly.assertThat(user.email.value).isEqualTo(command.email)
                softly.assertThat(user.birthDate.value).isEqualTo(command.birthDate)
                softly.assertThat(user.gender).isEqualTo(command.gender)
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
