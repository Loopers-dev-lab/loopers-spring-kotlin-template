package com.loopers.domain.user

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDate

class UserTest {
    @Test
    fun `사용자를 생성할 수 있다`() {
        // given
        val name = "홍길동"
        val email = "hong@example.com"
        val gender = Gender.MALE
        val birthDate = LocalDate.of(1990, 1, 1)

        // when
        val user = User(
            name = name,
            email = email,
            gender = gender,
            birthDate = birthDate,
        )

        // then
        assertThat(user.name).isEqualTo(name)
        assertThat(user.email).isEqualTo(email)
        assertThat(user.gender).isEqualTo(gender)
        assertThat(user.birthDate).isEqualTo(birthDate)
    }

    @Test
    fun `이름이 비어있으면 예외가 발생한다`() {
        assertThatThrownBy {
            User(
                name = "",
                email = "hong@example.com",
                gender = Gender.MALE,
                birthDate = LocalDate.of(1990, 1, 1),
            )
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("이름")
    }

    @Test
    fun `이메일이 비어있으면 예외가 발생한다`() {
        assertThatThrownBy {
            User(
                name = "홍길동",
                email = "",
                gender = Gender.MALE,
                birthDate = LocalDate.of(1990, 1, 1),
            )
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("이메일")
    }

    @Test
    fun `미래의 생년월일은 허용되지 않는다`() {
        assertThatThrownBy {
            User(
                name = "홍길동",
                email = "hong@example.com",
                gender = Gender.MALE,
                birthDate = LocalDate.now().plusDays(1),
            )
        }.isInstanceOf(CoreException::class.java)
            .hasMessageContaining("생년월일")
    }
}
