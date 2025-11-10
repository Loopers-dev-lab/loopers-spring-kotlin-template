package com.loopers.domain.point

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test

class PointTest {

    @ParameterizedTest(name = "{0}원 충전 시도 => 예외 발생")
    @ValueSource(longs = [0L, -1L, -100L, -1000L])
    fun `0 이하의 금액으로 충전 시 예외가 발생한다`(chargeAmount: Long) {
        // given
        val point = Point.create(amount = 100L, userId = "user123")

        // when & then
        assertThatThrownBy {
            point.charge(chargeAmount)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("충전 금액은 0보다 커야 합니다.")
    }

    @Test
    fun `포인트를 여러 번 충전할 수 있다`() {
        // given
        val point = Point.create(amount = 1000L, userId = "user123")

        // when
        point.charge(500L)
        point.charge(300L)
        point.charge(200L)

        // then
        assertThat(point.amount.value).isEqualTo(2000L)
    }

    @Test
    fun `잔액이 부족하면 포인트 사용 시 예외가 발생한다`() {
        // given
        val point = Point.create(amount = 1000L, userId = "user123")

        // when & then
        assertThatThrownBy {
            point.use(1500L)
        }.isInstanceOf(CoreException::class.java)
            .extracting("errorType")
            .isEqualTo(ErrorType.INSUFFICIENT_BALANCE)
    }

    @Test
    fun `잔액이 0일 때 포인트 사용 시 예외가 발생한다`() {
        // given
        val point = Point.create(amount = 1000L, userId = "user123")
        point.use(1000L)

        // when & then
        assertThatThrownBy {
            point.use(100L)
        }.isInstanceOf(CoreException::class.java)
            .extracting("errorType")
            .isEqualTo(ErrorType.INSUFFICIENT_BALANCE)
    }

    @ParameterizedTest(name = "1000원 보유 시 {0}원 사용 시도 => 예외 발생")
    @ValueSource(longs = [1001L, 1500L, 2000L, 10000L])
    fun `잔액보다 많은 포인트 사용 시 예외가 발생한다`(useAmount: Long) {
        // given
        val point = Point.create(amount = 1000L, userId = "user123")

        // when & then
        assertThatThrownBy {
            point.use(useAmount)
        }.isInstanceOf(CoreException::class.java)
            .extracting("errorType")
            .isEqualTo(ErrorType.INSUFFICIENT_BALANCE)
    }

    @ParameterizedTest(name = "0 이하의 값({0}) 사용 시도 => 예외 발생")
    @ValueSource(longs = [0L, -1L, -100L, -1000L])
    fun `0 이하의 금액으로 사용 시 예외가 발생한다`(invalidAmount: Long) {
        // given
        val point = Point.create(amount = 1000L, userId = "user123")

        // when & then
        assertThatThrownBy {
            point.use(invalidAmount)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("사용 금액은 0보다 커야 합니다")
    }

    @Test
    fun `포인트 충전과 사용을 반복할 수 있다`() {
        // given
        val point = Point.create(amount = 1000L, userId = "user123")

        // when
        point.charge(500L)
        assertThat(point.amount.value).isEqualTo(1500L)

        point.use(300L)
        assertThat(point.amount.value).isEqualTo(1200L)

        point.charge(800L)
        assertThat(point.amount.value).isEqualTo(2000L)

        point.use(500L)
        assertThat(point.amount.value).isEqualTo(1500L)
    }
}
