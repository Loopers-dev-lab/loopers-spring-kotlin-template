package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate

class UserTest {
    companion object {
        private const val ANY_USER_NAME = "username"
        private const val ANY_BIRTH = "2000-01-01"
        private const val ANY_EMAIL = "toong@toong.io"
        private const val ANY_POINT_BALANCE = 231231
        private val ANY_GENDER = Gender.MALE
    }

    @DisplayName("회원가입 테스트")
    @Nested
    inner class SignUp {

        @DisplayName("ID가 영문 및 숫자 10자 이내이거나 공백이 아니면 가입에 성공한다.")
        @ParameterizedTest
        @ValueSource(strings = ["chelsea", "kiwoom10th", "111"])
        fun signUpUser_whenUsernameIsValid(validUsername: String) {
            // when
            val user = User.signUp(
                username = validUsername,
                birth = ANY_BIRTH,
                email = ANY_EMAIL,
                gender = ANY_GENDER,
            )

            // then
            assertThat(user.username).isEqualTo(validUsername)
        }

        @DisplayName("ID가 영문 및 숫자 10자 이내가 아니거나 공백이면 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["chelseaIs9th", "aaaaaaaaaaaa", "첼시또졌어?", "chelsea패배", ""])
        fun throwsBadRequestException_whenUsernameIsInvalid(invalidUsername: String) {
            // when
            val exception = assertThrows<CoreException> {
                User.signUp(
                    username = invalidUsername,
                    birth = ANY_BIRTH,
                    email = ANY_EMAIL,
                    gender = ANY_GENDER,
                )
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("id는 영문 대소문자, 숫자만 가능합니다.")
        }

        @DisplayName("이메일이 xx@yy.zz 형식이 아니라면 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["chel@naver.", "sea.com", "9th@naver.com?", "chel@.com", ""])
        fun throwsBadRequestException_whenEmailIsInvalid(invalidEmail: String) {
            // when
            val exception = assertThrows<CoreException> {
                User.signUp(
                    username = ANY_USER_NAME,
                    birth = ANY_BIRTH,
                    email = invalidEmail,
                    gender = ANY_GENDER,
                )
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("이메일 형식이 올바르지 않습니다.")
        }

        @DisplayName("이메일이 xx@yy.zz 형식이면 가입에 성공한다.")
        @ParameterizedTest
        @ValueSource(strings = ["chelsea@naver.com", "toong@mineis.io", "1@a.com"])
        fun signUpUser_whenEmailIsValid(validEmail: String) {
            // when
            val user = User.signUp(
                username = ANY_USER_NAME,
                birth = ANY_BIRTH,
                email = validEmail,
                gender = ANY_GENDER,
            )

            // then
            assertThat(user.email).isEqualTo(validEmail)
        }

        @DisplayName("생년월일이 yyyy-MM-dd 형식이 아니거나 존재하지 않는 날짜인 경우 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["1994-9-23", "19940923", "2025-02-50", "94-09-23", ""])
        fun throwsBadRequestException_whenBirthIsInvalid(invalidBirth: String) {
            // when
            val exception = assertThrows<CoreException> {
                User.signUp(
                    username = ANY_USER_NAME,
                    birth = invalidBirth,
                    email = ANY_EMAIL,
                    gender = ANY_GENDER,
                )
            }

            // then
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).isEqualTo("생년월일 형식이 올바르지 않습니다.")
        }

        @DisplayName("생년월일이 yyyy-MM-dd 형식이면 가입에 성공한다.")
        @ParameterizedTest
        @ValueSource(strings = ["1994-09-23", "2025-10-30"])
        fun signUpUser_whenBirthIsValid(validBirth: String) {
            // when
            val user = User.signUp(
                username = ANY_USER_NAME,
                birth = validBirth,
                email = ANY_EMAIL,
                gender = ANY_GENDER,
            )

            // then
            val parsedBirthDate = LocalDate.parse(validBirth)
            assertThat(user.birth).isEqualTo(parsedBirthDate)
        }

        @DisplayName("성별이 주어지면 가입에 성공한다.")
        @Test
        fun signUpUser_whenGenderIsProvided() {
            // when
            val providedGender = Gender.FEMALE
            val user = User.signUp(
                username = ANY_USER_NAME,
                birth = ANY_BIRTH,
                email = ANY_EMAIL,
                gender = providedGender,
            )

            // then
            assertThat(user.gender).isEqualTo(providedGender)
        }
    }
}
