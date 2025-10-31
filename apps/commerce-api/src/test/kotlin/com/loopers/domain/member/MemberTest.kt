package com.loopers.domain.member

import com.loopers.domain.shared.Email
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class MemberTest {

    @DisplayName("유효한 유저정보로 Member 객체를 생성할 수 있다")
    @Test
    fun createValidMember() {
        val memberId = MemberId("testUser1")
        val email = Email("test@example.com")
        val birthDate = BirthDate.from("1990-01-01")
        val gender = Gender.MALE

        val member = Member(memberId, email, birthDate, gender)

        assertAll(
            { assertThat(member.memberId.value).isEqualTo(memberId.value) },
            { assertThat(member.email.address).isEqualTo(email.address) },
            { assertThat(member.birthDate).isEqualTo(birthDate)},
            { assertThat(member.gender.name).isEqualTo(gender.name)}
        )
    }
}
