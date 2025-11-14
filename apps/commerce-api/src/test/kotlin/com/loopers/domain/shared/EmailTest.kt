package com.loopers.domain.shared

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class EmailTest {

    @DisplayName("유효한 이메일을 입력하면 Email 객체가 생성된다")
    @Test
    fun createValidEmail() {
        // arrange
        val address = "korea1@gmail.com"

        // act
        val email = Email(address)

        // assert
        assertThat(email.address).isEqualTo(address)
    }

    @DisplayName("유효하지 않은 이메일을 입력하면 InvalidEmailPatternException이 발생한다")
    @ParameterizedTest
    @ValueSource(strings = ["", "abc123kor123", "@!@!#$%$"])
    fun invalidEmailPatternException(email: String) {
        assertThrows<CoreException> { Email(email) }
    }

}
