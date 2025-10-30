package com.loopers.domain.user.vo

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

class BirthDateTest {

    @DisplayName("올바른 yyyy-MM-dd 형식의 날짜가 주어지면, 정상적으로 생성된다.")
    @Test
    fun createsBirthDate_whenValidFormatIsGiven() {
        // arrange
        val dateString = "1999-02-15"

        // act
        val birthDate = BirthDate(dateString)

        // assert
        assertThat(birthDate.value).isEqualTo(LocalDate.of(1999, 2, 15))
    }

    @DisplayName("잘못된 형식의 날짜가 주어지면, 예외가 발생한다.")
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "99-02-15, 연도가 2자리인 경우",
        "1999/02/15, 슬래시로 구분된 경우",
        "1999-2-15, 월이 1자리인 경우",
        "1999-13-01, 13월은 존재하지 않음",
        "invalid-date, 완전히 잘못된 형식",
    )
    fun throwsException_whenInvalidFormatIsGiven(invalidDate: String, testCase: String) {
        // act & assert
        val exception = assertThrows<IllegalArgumentException> {
            BirthDate(invalidDate)
        }

        assertThat(exception.message).isEqualTo("생년월일은 yyyy-MM-dd 형식입니다.")
    }
}
