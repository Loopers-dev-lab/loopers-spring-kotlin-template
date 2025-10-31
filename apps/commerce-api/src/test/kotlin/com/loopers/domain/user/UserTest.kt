package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class UserTest {

    @DisplayName("User 객체를 생성할 때")
    @Nested
    inner class Create {

        @DisplayName("모든 필드가 유효하면, 정상적으로 생성된다.")
        @Test
        fun createsUser_whenAllFieldsAreValid() {
            // arrange
            val username = "user123"
            val email = "user@example.com"
            val birthDate = "1997-03-25"
            val gender = User.Gender.MALE

            // act
            val user = User(
                username = username,
                email = email,
                birthDate = birthDate,
                gender = gender,
            )

            // assert
            assertAll(
                { assertThat(user.username).isEqualTo(username) },
                { assertThat(user.email).isEqualTo(email) },
                { assertThat(user.birthDate).isEqualTo(birthDate) },
                { assertThat(user.gender).isEqualTo(gender) },
            )
        }

        @DisplayName("아이디가 영문 및 숫자 10자 이내 형식에 맞지 않으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @MethodSource("com.loopers.domain.user.UserTest#invalidUsernames")
        fun throwsBadRequestException_whenUsernameIsInvalid(username: String) {
            // act
            val result = assertThrows<CoreException> {
                User(
                    username = username,
                    email = "user@example.com",
                    birthDate = "1997-03-25",
                    gender = User.Gender.MALE,
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이메일 형식이 올바르지 않으면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @MethodSource("com.loopers.domain.user.UserTest#invalidEmails")
        fun throwsBadRequestException_whenEmailIsInvalid(email: String) {
            // act
            val result = assertThrows<CoreException> {
                User(
                    username = "user123",
                    email = email,
                    birthDate = "1997-03-25",
                    gender = User.Gender.MALE,
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("생년월일 형식이 yyyy-MM-dd 또는 유효한 년 월 일이 아니면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @MethodSource("com.loopers.domain.user.UserTest#invalidBirthDates")
        fun throwsBadRequestException_whenBirthDateIsInvalid(birthDate: String) {
            // act
            val result = assertThrows<CoreException> {
                User(
                    username = "user123",
                    email = "user@example.com",
                    birthDate = birthDate,
                    gender = User.Gender.MALE,
                )
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    companion object {
        @JvmStatic
        fun invalidUsernames(): Stream<String> = Stream.of(
            "",
            "user1234567",
            "user@123",
            "유저123",
            "user 123",
            "user_123",
            "user-123",
        )

        @JvmStatic
        fun invalidEmails(): Stream<String> = Stream.of(
            "",
            "userexample.com",
            "@example.com",
            "user@",
            "user@example",
            "user name@example.com",
        )

        @JvmStatic
        fun invalidBirthDates(): Stream<String> = Stream.of(
            "",
            "1997/03/25",
            "1997.03.25",
            "19970325",
            "97-03-25",
            "1997-3-25",
            "1997-03-5",
            "1997-13-01",
            "1997-00-01",
            "1997-01-32",
        )
    }
}
