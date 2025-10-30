package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.test.KSelect
import com.loopers.utils.DatabaseCleanUp
import com.ninjasquad.springmockk.SpykBean
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.instancio.Instancio
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

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

    @DisplayName("회원가입 통합테스트")
    @Nested
    inner class SignUp {
        val correctUsername = "username"
        val correctBirth = "2000-01-01"
        val correctEmail = "toong@toong.io"
        val correctGender = Gender.MALE

        @DisplayName("기가입자와 username가 충돌하지 않는 경우 User가 저장된다.")
        @Test
        fun saveUser_whenUsernameIsUnique() {
            // given
            val userSignUpRequest = UserCommand.SignUp(correctUsername, correctBirth, correctEmail, correctGender)

            // when
            val signUpUser = userService.signUp(userSignUpRequest)

            // then
            val user = userRepository.findById(signUpUser.id)

            assertAll(
                { assertThat(user).isNotNull() },
                { assertThat(user?.username).isEqualTo(correctUsername) },
            )
        }

        @DisplayName("회원 가입시 User 저장이 수행된다")
        @Test
        fun saveUser_whenSignUp() {
            // given
            val userSignUpRequest = UserCommand.SignUp(
                correctUsername,
                correctBirth,
                correctEmail,
                correctGender,
            )

            // when
            userService.signUp(userSignUpRequest)

            // then - 저장 호출 여부만 검증
            verify(exactly = 1) {
                userRepository.save(any())
            }
        }

        @DisplayName("이미 가입된 id로 가입하는 경우 실패한다.")
        @Test
        fun throwsBadRequestException_whenUsernameIsAlreadyExist() {
            // given
            val existingUsername = "testUsername"
            val existingUser = Instancio.of(User::class.java)
                .ignore(KSelect.field(User::id))
                .set(KSelect.field(User::username), existingUsername)
                .create()
            userRepository.save(existingUser)

            // when
            val userSignUpRequest = UserCommand.SignUp(existingUsername, correctBirth, correctEmail, correctGender)
            val exception = assertThrows<CoreException> { userService.signUp(userSignUpRequest) }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT)
            assertThat(exception.message).isEqualTo("이미 가입된 유저입니다.")
        }
    }
}
