package com.loopers.domain.point

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class PointTest {
    @ParameterizedTest(name = "[{index}] {1}")
    @MethodSource("invalidAmountCases")
    fun `0 이하의 정수로 포인트를 충전 시 실패한다`(amount: Int) {
        assertCreateFails(amount = amount)
    }

    // ✨ 공통 로직으로 추출
    private fun assertCreateFails(
        userId: Long = 1L,
        amount: Int = 100,
    ) {
        // when
        val result = assertThrows<CoreException> {
            Point.create(userId, amount)
        }

        // then
        assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
    }

    companion object {
        @JvmStatic
        fun invalidAmountCases() = listOf(
            Arguments.of("0", "경계값"),
            Arguments.of("-1", "적은값"),
        )
    }
}
