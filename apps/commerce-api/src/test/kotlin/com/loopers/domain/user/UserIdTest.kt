package com.loopers.domain.user

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource

class UserIdTest {

    @Test
    fun `올바른 형식의 사용자 ID로 객체 생성에 성공한다`() {
        // given
        val validUserId = "user123"

        // when
        val userId = UserId(validUserId)

        // then
        assertThat(userId.value).isEqualTo(validUserId)
        assertThat(userId.toString()).isEqualTo(validUserId)
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = [" ", "  "])
    fun `사용자 ID가 공백이거나 비어있으면 실패한다`(value: String?) {
        assertThatThrownBy {
            UserId(value ?: "")
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("userId는 필수입니다")
    }

    @Test
    fun `사용자 ID가 10자를 초과하면 실패한다`() {
        assertThatThrownBy {
            UserId("abcde_fghij")
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("userId는 10자 이내여야 합니다")
            .hasMessageContaining("최대 10자")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "user-123",
            "user_123",
            "user 123",
            "user@123",
            "한글user",
            "user!",
            "user#123",
            "user.name",
        ],
    )
    fun `사용자 ID에 영문과 숫자 외의 문자가 포함되면 실패한다`(invalidUserId: String) {
        assertThatThrownBy {
            UserId(invalidUserId)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("userId는 영문 및 숫자만 포함해야 합니다")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "user",
            "USER",
            "123",
            "user123",
            "USER123",
            "abc",
            "ABC123def",
            "a",
            "1234567890",
        ],
    )
    fun `다양한 형식의 올바른 사용자 ID로 생성에 성공한다`(validUserId: String) {
        // when
        val userId = UserId(validUserId)

        // then
        assertThat(userId.value).isEqualTo(validUserId)
    }
}
