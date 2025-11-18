package com.loopers.domain.member

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class MemberIdTest {

    @DisplayName("유효하지 않은 memberId를 입력하면 CoreException가 발생한다.")
    @ParameterizedTest
    @ValueSource(strings = ["", "abc123kor123", "@!@!#$%$"])
    fun invalidMemberIdException(memberId: String) {
        val exception = assertThrows<CoreException> { MemberId(memberId) }

        assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }
}
