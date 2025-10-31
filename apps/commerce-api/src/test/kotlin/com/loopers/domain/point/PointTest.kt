package com.loopers.domain.point

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class PointTest {

    @DisplayName("포인트를 충전할 때")
    @Nested
    inner class Charge {

        @DisplayName("0 이하의 정수로 포인트를 충전 시 BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @MethodSource("com.loopers.domain.point.PointTest#invalidChargeAmounts")
        fun throwsException_whenChargeAmountIsInvalid(amount: Long) {
            // arrange
            val point = Point.of(userId = 1L, initialAmount = 1000L)

            // act
            val exception = assertThrows<CoreException> {
                point.charge(amount)
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    companion object {
        @JvmStatic
        fun invalidChargeAmounts(): Stream<Long> = Stream.of(
            0L,
            -1L,
            -100L,
            -1000L,
        )
    }
}
