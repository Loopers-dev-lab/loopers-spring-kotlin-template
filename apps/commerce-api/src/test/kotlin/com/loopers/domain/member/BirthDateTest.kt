package com.loopers.domain.member

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class BirthDateTest {

    @DisplayName("유효한 형식의 날짜 문자열을 입력하면 BirthDate 객체가 생성된다.")
    @Test
    fun validBirthDateFrom() {
        val from1 = BirthDate.from("2000-01-01")
        assertThat(from1.value).isEqualTo("2000-01-01")
    }

    @DisplayName("유효하지 않은 형식의 날짜 문자열을 입력하면 InvalidBirthDateException이 발생한다.")
    @ParameterizedTest
    @ValueSource(strings = ["2025:01:32", "2024-01-32 22:22:22", ""])
    fun invalidBirthDateFormatException(birthDate: String) {
        assertThrows<InvalidBirthDateException> { BirthDate.from(birthDate) }
    }

}
