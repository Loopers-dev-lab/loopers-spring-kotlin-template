package com.loopers.domain.user

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class UserTest {

    @ParameterizedTest(name = "[{index}] userId={0} 일 때 생성 실패")
    @MethodSource("invalidUserIds")
    fun `영문 및 숫자 10자 이내가 아닐경우 생성에 실패한다`(userId: String) {
        assertThatThrownBy {
            createUser(userId = userId)
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("userId")
    }

    @ParameterizedTest(name = "[{index}] email={0} 일 때 생성 실패")
    @MethodSource("invalidEmails")
    fun `이메일 형식이 맞지 않으면 생성에 실패한다`(email: String) {
        assertThatThrownBy {
            createUser(email = email)
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("email")
    }

    @ParameterizedTest(name = "[{index}] birthDay={0} 일 때 생성 실패")
    @MethodSource("invalidBirthDates")
    fun `생년월일이 yyyy-MM-dd 형식에 맞지 않으면 User 생성에 실패한다`(birthDate: String) {
        assertThatThrownBy {
            createUser(birthDate = birthDate)
        }.isInstanceOf(IllegalArgumentException::class.java).satisfiesAnyOf(
            { error -> assertThat(error.message).contains("birthDate") },
            { error -> assertThat(error.message).contains("날짜") },
        )
    }

    @Test
    fun `유효성 검증에 통과할 경우 User 생성에 성공한다`() {
        // given
        val userId = "user123"
        val email = "test@example.com"
        val birthDate = "2000-01-01"
        val gender = Gender.MALE

        // when
        val user = createUser(
            userId = userId,
            email = email,
            birthDate = birthDate,
            gender = gender,
        )

        // then
        assertSoftly { softly ->
            softly.assertThat(user).isNotNull
            softly.assertThat(user.userId.value).isEqualTo(userId)
            softly.assertThat(user.email.value).isEqualTo(email)
            softly.assertThat(user.birthDate.value).isEqualTo(birthDate)
        }
    }

    private fun createUser(
        userId: String = "user123",
        email: String = "test@example.com",
        birthDate: String = "2000-01-01",
        gender: Gender = Gender.MALE,
    ): User {
        return User.create(
            UserCommand.SignUp(
                userId = userId,
                email = email,
                birthDate = birthDate,
                gender = gender,
            ),
        )
    }

    companion object {
        @JvmStatic
        fun invalidUserIds(): Stream<String> = Stream.of(
            "user12345678901",
            "",
            "user@123",
            "사용자123",
            "user 123",
            "user-123",
            " ",
            "user_123",
            "user.123",
            "1234567890a",
        )

        @JvmStatic
        fun invalidEmails(): Stream<String> = Stream.of(
            "test",
            "test@",
            "@example.com",
            "test@example",
            "test@@example.com",
            "test @example.com",
            "",
        )

        @JvmStatic
        fun invalidBirthDates(): Stream<String> = Stream.of(
            "2000/01/01",
            "2000.01.01",
            "20000101",
            "2000-1-1",
            "01-01-2000",
            "2000-13-01",
            "2000-01-32",
            "",
            "2000-02-30",
            "2000-00-01",
            "2000-01-00",
            " ",
            "abcd-ef-gh",
        )
    }
}
