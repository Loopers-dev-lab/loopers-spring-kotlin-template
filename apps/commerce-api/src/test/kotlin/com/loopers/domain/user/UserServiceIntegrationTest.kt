package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import com.ninjasquad.springmockk.SpykBean
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

@SpringBootTest
class UserServiceIntegrationTest @Autowired constructor(
    private val userService: UserService,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @SpykBean
    private lateinit var userRepository: UserRepository

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("유저 상세 조회 통합테스트")
    @Nested
    inner class Get {
        @DisplayName("해당 ID 의 회원이 존재할 경우, 회원 정보가 반환된다.")
        @Test
        fun returnUserInfo_whenAlreadyExistIdIsProvided() {
            // given
            val existUser = createUser()

            // when
            val userInfo = userService.findUserBy(existUser.id)

            // then
            assertAll(
                { assertThat(userInfo?.id).isEqualTo(existUser.id) },
                { assertThat(userInfo?.username).isEqualTo(existUser.username) },
                { assertThat(userInfo?.birth).isEqualTo(existUser.birth) },
                { assertThat(userInfo?.email).isEqualTo(existUser.email) },
                { assertThat(userInfo?.gender).isEqualTo(existUser.gender) },
            )
        }

        @DisplayName("해당 ID 의 회원이 존재하지 않을 경우, null 이 반환된다.")
        @Test
        fun returnNull_whenNotExistIdIsProvided() {
            // when
            val notExistId = 999L
            val user = userService.findUserBy(notExistId)

            // then
            assertThat(user).isNull()
        }
    }

    @DisplayName("회원가입 통합테스트")
    @Nested
    inner class SignUp {
        @DisplayName("회원 가입시 User 저장이 수행된다")
        @Test
        fun saveUser_whenSignUp() {
            // when
            val command = signUpCommand()
            userService.signUp(command)

            // then
            verify(atLeast = 1) {
                userRepository.save(match { it.username == command.username })
            }
        }

        @DisplayName("회원가입을 하면 User가 저장된다.")
        @Test
        fun saveUser_whenUsernameIsUnique() {
            // when
            val command = signUpCommand()
            val signUpUser = userService.signUp(command)

            // then
            val savedUser = userRepository.findById(signUpUser.id)

            assertAll(
                { assertThat(savedUser).isNotNull() },
                { assertThat(savedUser?.username).isEqualTo(command.username) },
                { assertThat(savedUser?.birth).isEqualTo(LocalDate.parse(command.birth)) },
                { assertThat(savedUser?.email).isEqualTo(command.email) },
                { assertThat(savedUser?.gender).isEqualTo(command.gender) },
            )
        }

        @DisplayName("이미 가입된 username으로 가입하는 경우 실패한다.")
        @Test
        fun throwsBadRequestException_whenUsernameIsAlreadyExist() {
            // given
            val existUsername = "testName"
            val existUser = createUser(
                username = existUsername,
            )

            // when
            val command = signUpCommand(
                username = existUser.username,
            )
            val exception = assertThrows<CoreException> { userService.signUp(command) }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT)
            assertThat(exception.message).isEqualTo("이미 가입된 유저입니다.")
        }
    }

    private fun createUser(
        username: String = "username",
        birth: LocalDate = LocalDate.of(2000, 1, 1),
        email: String = "test@example.com",
        gender: Gender = Gender.MALE,
    ): User {
        val user = User.of(username, birth, email, gender)
        return userRepository.save(user)
    }

    private fun signUpCommand(
        username: String = "username",
        birth: String = "2000-01-01",
        email: String = "test@example.com",
        gender: Gender = Gender.MALE,
    ) = UserCommand.SignUp(username, birth, email, gender)
}
