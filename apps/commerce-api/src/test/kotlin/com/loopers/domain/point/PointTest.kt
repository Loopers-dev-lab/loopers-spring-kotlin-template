package com.loopers.domain.point

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test

class PointTest {

    @ParameterizedTest
    @ValueSource(longs = [0, -1, -100, -1000])
    fun `0 이하의 정수로 포인트를 충전 시 실패한다`(amount: Long) {
        assertThatThrownBy {
            createPoint(amount = amount)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("충전 금액은 0보다 커야 합니다.")
    }

    @Test
    fun `포인트 충전에 성공한다`() {
        // given
        val userId = "user123"
        val amount = 100L

        val point = createPoint(
            userId = userId,
            amount = amount,
        )

        // when
        point.charge(200L)

        // then
        assertSoftly { softly ->
            softly.assertThat(point).isNotNull
            softly.assertThat(point.userId.value).isEqualTo(userId)
            softly.assertThat(point.amount.value).isEqualTo(300L)
        }
    }

    private fun createPoint(
        userId: String = "user123",
        amount: Long = 100L,
    ): Point {
        return Point.create(
            amount = amount,
            userId = userId,
        )
    }
}
