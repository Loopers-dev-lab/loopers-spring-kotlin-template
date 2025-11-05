package com.loopers.domain.member

import org.assertj.core.api.AssertionsForClassTypes
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PointTest {

    @DisplayName("유효한 포인트 금액으로 Point 객체를 생성할 수 있다")
    @Test
    fun createValidPoint() {
        val point = Point(1000L)

        AssertionsForClassTypes.assertThat(point.amount).isEqualTo(1000L)
    }

    @DisplayName("0 이하의 정수로 포인트를 생성 시 InvalidPointAmountException이 발생한다")
    @ParameterizedTest
    @ValueSource(longs = [-1L, -100L, -1000L, -999999L])
    fun createPointWithVariousNegativeAmounts(amount: Long) {
        assertThrows<InvalidPointAmountException> { Point(amount) }
    }
}
