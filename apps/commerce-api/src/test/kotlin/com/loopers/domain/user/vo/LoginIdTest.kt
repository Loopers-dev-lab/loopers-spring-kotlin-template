package com.loopers.domain.user.vo

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class LoginIdTest {

    @DisplayName("올바른 형식의 아이디가 주어지면, 정상적으로 생성된다.")
    @Test
    fun createsLoginId_whenValidFormatIsGiven() {
        // arrange
        val validLoginId = "test1234"

        // act
        val loginId = LoginId(validLoginId)

        // assert
        assertThat(loginId.value).isEqualTo(validLoginId)
    }

    @DisplayName("잘못된 형식의 아이디가 주어지면, 예외가 발생한다.")
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "abcdefgh123, 10자 초과",
        "test@123, 특수문자 포함",
        "테스트123, 한글 포함",
        "test 123, 공백 포함",
        "'', 빈 문자열",
    )
    fun throwsException_whenInvalidFormatIsGiven(invalidLoginId: String, testCase: String) {
        // act & assert
        val exception = assertThrows<IllegalArgumentException> {
            LoginId(invalidLoginId)
        }

        assertThat(exception.message).isEqualTo("ID는 영문 및 숫자 10자 이내여야 합니다")
    }
}
