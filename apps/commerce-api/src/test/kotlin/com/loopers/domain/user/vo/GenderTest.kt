package com.loopers.domain.user.vo

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class GenderTest {

    @DisplayName("올바른 문자열이 주어지면, 정상적으로 변환된다.")
    @Test
    fun createsGender_whenValidStringIsGiven() {
        // arrange
        val input = "male"

        // act
        val gender = Gender.from(input)

        // assert
        assertThat(gender).isEqualTo(Gender.MALE)
    }

    @DisplayName("잘못된 문자열이 주어지면, 예외가 발생한다.")
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "UNKNOWN, 지원하지 않는 값",
        "MAN, 잘못된 영어 표현",
        "M, 약자",
        "OTHER, 기타",
        "'', 빈 문자열",
    )
    fun throwsException_whenInvalidStringIsGiven(invalidGender: String, testCase: String) {
        // act & assert
        assertThrows<IllegalArgumentException> {
            Gender.from(invalidGender)
        }
    }
}
