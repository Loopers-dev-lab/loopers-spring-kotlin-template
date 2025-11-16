package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class UserServiceTest {
    private val userRepository = mockk<UserRepository>()
    private val userService = UserService(userRepository)

    @DisplayName("회원가입할 때,")
    @Nested
    inner class RegisterUser {
        @DisplayName("이메일이 중복되지 않은 경우, 회원가입에 성공한다")
        @Test
        fun registerUser_whenEmailIsUnique_thenSuccess() {
            // given
            val name = "홍길동"
            val email = "hong@example.com"
            val gender = Gender.MALE
            val birthDate = LocalDate.of(1990, 1, 1)

            every { userRepository.existsByEmail(email) } returns false
            every { userRepository.save(any()) } answers { firstArg() }

            // when
            val result = userService.registerUser(name, email, gender, birthDate)

            // then
            assertThat(result.name).isEqualTo(name)
            assertThat(result.email).isEqualTo(email)
            assertThat(result.gender).isEqualTo(gender)
            assertThat(result.birthDate).isEqualTo(birthDate)
            verify(exactly = 1) { userRepository.existsByEmail(email) }
            verify(exactly = 1) { userRepository.save(any()) }
        }

        @DisplayName("이메일이 이미 존재하는 경우, 예외를 발생시킨다")
        @Test
        fun registerUser_whenEmailAlreadyExists_thenThrowsException() {
            // given
            val email = "duplicate@example.com"
            every { userRepository.existsByEmail(email) } returns true

            // when & then
            val exception = assertThrows<CoreException> {
                userService.registerUser(
                    name = "테스트",
                    email = email,
                    gender = Gender.FEMALE,
                    birthDate = LocalDate.of(1995, 5, 5),
                )
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).contains("이미 사용 중인 이메일")
            verify(exactly = 1) { userRepository.existsByEmail(email) }
            verify(exactly = 0) { userRepository.save(any()) }
        }
    }

    @DisplayName("사용자를 조회할 때,")
    @Nested
    inner class GetUser {
        @DisplayName("사용자가 존재하는 경우, 사용자 정보를 반환한다")
        @Test
        fun getUser_whenUserExists_thenReturnsUser() {
            // given
            val userId = 1L
            val user = User(
                name = "김철수",
                email = "kim@example.com",
                gender = Gender.MALE,
                birthDate = LocalDate.of(1985, 3, 15),
            )
            every { userRepository.findById(userId) } returns user

            // when
            val result = userService.getUser(userId)

            // then
            assertThat(result).isEqualTo(user)
            verify(exactly = 1) { userRepository.findById(userId) }
        }

        @DisplayName("사용자가 존재하지 않는 경우, 예외를 발생시킨다")
        @Test
        fun getUser_whenUserNotFound_thenThrowsException() {
            // given
            val userId = 999L
            every { userRepository.findById(userId) } returns null

            // when & then
            val exception = assertThrows<CoreException> {
                userService.getUser(userId)
            }

            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
            assertThat(exception.message).contains("사용자를 찾을 수 없습니다")
            verify(exactly = 1) { userRepository.findById(userId) }
        }
    }
}
