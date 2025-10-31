package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UserTest {
    @DisplayName("유저를 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("ID가 영문 및 숫자 10자 이내 형식에 맞지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenIdIsInvalid() {
            // arrange
            val id = "invalid-id"

            // act
            val result = assertThrows<CoreException> {
                User(userId = id, email = "test@test.com", birth = "2025-10-25", gender = Gender.OTHER)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이메일이 xx@yy.zz 형식에 맞지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenEmailIsInvalid() {
            // arrange
            val email = "invalid-email"

            // act
            val result = assertThrows<CoreException> {
                User(userId = "testId", email = email, birth = "2025-10-25", gender = Gender.OTHER)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("생년월일이 yyyy-MM-dd 형식에 맞지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequestException_whenBirthIsInvalid() {
            // arrange
            val birth = "25-10-2025"

            // act
            val result = assertThrows<CoreException> {
                User(userId = "testId", email = "test@test.com", birth = birth, gender = Gender.OTHER)
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
