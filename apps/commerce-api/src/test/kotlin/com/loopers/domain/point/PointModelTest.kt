package com.loopers.domain.point

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PointModelTest {
    @DisplayName("0 이하의 정수로 포인트를 충전 시 실패한다.")
    @Test
    fun fail_whenUnderZeroCharged() {
        // arrange
        val pointModel = PointFixture.create(userId = 0, balance = 0L)

        // act & assert
        val exception = assertThrows<IllegalArgumentException> {
            pointModel.charge(-1L)
        }
        print(exception.message)
        assertThat(exception.message).isEqualTo("충전 금액은 0보다 커야 합니다.")
    }
}
