package com.loopers.domain.user

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource

class EmailTest {

    @ParameterizedTest
    @ValueSource(
        strings = [
            "user@example.com",
            "user.name@example.com",
            "user+tag@example.co.kr",
            "user123@test-domain.com",
            "a@b.c",
        ],
    )
    fun `다양한 형식의 올바른 이메일로 생성에 성공한다`(validEmail: String) {
        // when
        val email = Email(validEmail)

        // then
        assertThat(email.value).isEqualTo(validEmail)
    }

    @ParameterizedTest
    @NullAndEmptySource
    fun `이메일이 공백이거나 비어있으면 실패한다`(value: String?) {
        assertThatThrownBy {
            Email(value ?: "")
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("email은 필수입니다")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "user @example.com",
            "user@ example.com",
            "user@example .com",
            " user@example.com",
            "user@example.com ",
        ],
    )
    fun `이메일에 공백이 포함되면 실패한다`(emailWithWhitespace: String) {
        assertThatThrownBy {
            Email(emailWithWhitespace)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("email에 공백이 포함될 수 없습니다")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "plaintext",
            "@example.com",
            "user@",
            "user@@example.com",
            "user@example",
            "user.example.com",
            "user@.com",
            "@",
            "user@domain@example.com",
        ],
    )
    fun `이메일 형식이 올바르지 않으면 실패한다`(invalidEmail: String) {
        assertThatThrownBy {
            Email(invalidEmail)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("email은 xx@yy.zz 형식이어야 합니다")
    }
}
