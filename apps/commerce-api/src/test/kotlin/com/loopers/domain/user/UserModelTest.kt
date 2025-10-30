package com.loopers.domain.user

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class UserModelTest {

    @DisplayName("유저 모델을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("로그인 아이디,이메일,생년월일,성별이 모두 주어지면, 정상적으로 생성된다.")
        @Test
        fun createsUserModel_whenLoginIdAndEmailAndBirthAreProvided() {
            // arrange
            val loginId = "qwer123"
            val email = "qwer123@naver.com"
            val birthDate = "1999-02-15"
            val gender = "MALE"

            // act
            val user = UserFixture.create(
                loginId = loginId,
                email = email,
                birthDate = birthDate,
                gender = gender,
            )

            // assert
            assertAll(
                { assertThat(user.id).isNotNull() },
                { assertThat(user.loginId.value).isEqualTo(loginId) },
                { assertThat(user.email.value).isEqualTo(email) },
                { assertThat(user.birthDate.value).isEqualTo(birthDate) },
                { assertThat(user.gender.name).isEqualTo(gender) },
            )
        }

        @DisplayName("로그인 아이디가 형식에 맞지 않으면, 객체 생성에 실패한다.")
        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource(
            "sonjs123321312, 10자 초과",
            "로그인아이디123, 한글 포함",
            "login!@#, 특수문자 포함",
        )
        fun createsUserModel_whenLoginIdIsInvalid(invalidLoginId: String, testCase: String) {
            // act & assert
            val exception = assertThrows<IllegalArgumentException> {
                UserFixture.create(loginId = invalidLoginId)
            }

            assertThat(exception.message).isEqualTo("ID는 영문 및 숫자 10자 이내여야 합니다")
        }

        @DisplayName("이메일이 형식에 맞지 않으면, 객체 생성에 실패한다.")
        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource(
            "sonjs7554@, @ 뒤에 도메인이 없는 경우",
            "sonjs7554, @가 없는 경우",
            "sonjs7554@gmail, .이 없는 경우",
            "@gmail.com, @ 앞에 로컬파트가 없는 경우",
        )
        fun createsUserModel_whenEmailIsInvalid(invalidEmail: String, testCase: String) {
            // act,assert
            val invalidEmailException = assertThrows<IllegalArgumentException> {
                UserFixture.create(email = invalidEmail)
            }
            assertThat(invalidEmailException.message).isEqualTo("이메일은 xx@yy.zz 형식이어야 합니다")
        }
    }
}
