package com.loopers.domain.user

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource

class BirthDateTest {

    @Test
    fun `올바른 형식의 생년월일로 객체 생성에 성공한다`() {
        // given
        val validDate = "1990-01-01"

        // when
        val birthDate = BirthDate(validDate)

        // then
        assertThat(birthDate.value).isEqualTo(validDate)
        assertThat(birthDate.toString()).isEqualTo(validDate)
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = [" ", "  "])
    fun `생년월일이 공백이거나 비어있으면 실패한다`(value: String?) {
        assertThatThrownBy {
            BirthDate(value ?: "")
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("birthDate는 필수입니다")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "2000/01/01",
            "2000.01.01",
            "20000101",
            "2000-1-1",
            "01-01-2000",
        ],
    )
    fun `생년월일 형식이 yyyy-MM-dd가 아니면 실패한다`(invalidDate: String) {
        assertThatThrownBy {
            BirthDate(invalidDate)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("birthDate는 yyyy-MM-dd 형식이어야 합니")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "2023-02-29",
            "2023-13-01",
            "2023-00-01",
            "2023-01-32",
            "2023-01-00",
            "2023-04-31",
        ],
    )
    fun `유효하지 않은 날짜로 생성 시 실패한다`(invalidDate: String) {
        assertThatThrownBy {
            BirthDate(invalidDate)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("유효하지 않은 날짜입니다")
    }
}
