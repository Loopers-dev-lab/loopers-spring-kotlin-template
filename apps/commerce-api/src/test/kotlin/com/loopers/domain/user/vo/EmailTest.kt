package com.loopers.domain.user.vo

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class EmailTest {

    @DisplayName("올바른 이메일 형식이 주어지면, 정상적으로 생성된다.")
    @Test
    fun createsEmail_whenValidFormatIsGiven() {
        // arrange
        val validEmail = "test@example.com"

        // act
        val email = Email(validEmail)

        // assert
        assertThat(email.value).isEqualTo(validEmail)
    }

    @DisplayName("잘못된 이메일 형식이 주어지면, 예외가 발생한다.")
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "test@, @ 뒤에 도메인이 없는 경우",
        "test, @가 없는 경우",
        "test@domain, .이 없는 경우",
        "@domain.com, @ 앞에 로컬파트가 없는 경우",
        "test@@domain.com, @가 두 개인 경우",
    )
    fun throwsException_whenInvalidFormatIsGiven(invalidEmail: String, testCase: String) {
        // act & assert
        val exception = assertThrows<IllegalArgumentException> {
            Email(invalidEmail)
        }

        assertThat(exception.message).isEqualTo("이메일은 xx@yy.zz 형식이어야 합니다")
    }
}
