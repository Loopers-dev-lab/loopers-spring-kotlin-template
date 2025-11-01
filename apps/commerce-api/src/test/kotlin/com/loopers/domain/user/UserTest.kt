package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UserTest {
    @DisplayName("회원 가입시,")
    @Nested
    inner class Create {
        @DisplayName("ID가 영문 및 숫자 10자 이내 형식에 맞지 않으면, User 객체 생성에 실패한다.")
        @Test
        fun failsToCreateUser_whenUserIdFormatIsInvalid() {
            // arrange
            val invalidUserId = "invalid_id"

            // act
            val exception = assertThrows<CoreException> {
                User(
                    userId = invalidUserId,
                    email = "test@example.com",
                    birthDate = "1990-01-01",
                    gender = Gender.MALE,
                )
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이메일이 xx@yy.zz 형식에 맞지 않으면, User 객체 생성에 실패한다.")
        @Test
        fun failsToCreateUser_whenEmailFormatIsInvalid() {
            // arrange
            val invalidEmail = "invalid-email"

            // act
            val exception = assertThrows<CoreException> {
                User(
                    userId = "testuser",
                    email = invalidEmail,
                    birthDate = "1990-01-01",
                    gender = Gender.MALE,
                )
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("생년월일이 yyyy-MM-dd 형식에 맞지 않으면, User 객체 생성에 실패한다.")
        @Test
        fun failsToCreateUser_whenBirthDateFormatIsInvalid() {
            // arrange
            val invalidBirthDate = "01-01-1990"

            // act
            val exception = assertThrows<CoreException> {
                User(
                    userId = "testuser",
                    email = "test@example.com",
                    birthDate = invalidBirthDate,
                    gender = Gender.MALE,
                )
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("포인트 충전시,")
    @Nested
    inner class ChargePoint {
        @DisplayName("0 이하의 정수로 포인트를 충전 시 실패한다.")
        @Test
        fun failsToChargePoint_whenAmountIsZeroOrNegative() {
            // arrange
            val user = User(
                userId = "testuser",
                email = "test@example.com",
                birthDate = "1990-01-01",
                gender = Gender.MALE,
            )

            // act
            val exception = assertThrows<CoreException> {
                user.chargePoint(0)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
