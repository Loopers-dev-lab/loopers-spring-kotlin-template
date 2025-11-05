package com.loopers.domain.member

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class MemberIdTest {

    @DisplayName("유효하지 않은 userId를 입력하면 InvalidMemberIdException가 발생한다.")
    @ParameterizedTest
    @ValueSource(strings = ["", "abc123kor123", "@!@!#$%$"])
    fun invalidMemberIdException(userId: String) {
        assertThrows<InvalidMemberIdException> { MemberId(userId) }
    }
}
