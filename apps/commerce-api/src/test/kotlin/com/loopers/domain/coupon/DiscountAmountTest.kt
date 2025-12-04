package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("DiscountAmount 단위 테스트")
class DiscountAmountTest {

    @DisplayName("불변식 검증")
    @Nested
    inner class InvariantValidation {

        @DisplayName("할인 금액이 0 이하면 예외가 발생한다")
        @Test
        fun `throw exception when discount value is zero or negative`() {
            // when & then
            val exception = assertThrows<CoreException> {
                DiscountAmount(
                    type = DiscountType.FIXED_AMOUNT,
                    value = 0,
                )
            }
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).contains("할인 금액은 0보다 커야 합니다")
        }

        @DisplayName("정률 할인 비율이 100을 초과하면 예외가 발생한다")
        @Test
        fun `throw exception when rate discount exceeds 100`() {
            // when & then
            val exception = assertThrows<CoreException> {
                DiscountAmount(
                    type = DiscountType.RATE,
                    value = 101,
                )
            }
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(exception.message).contains("할인율은 100 이하여야 합니다")
        }

        @DisplayName("정률 할인 비율이 100이면 정상적으로 생성된다")
        @Test
        fun `create successfully when rate discount is 100`() {
            // when & then
            val discountAmount = DiscountAmount(
                type = DiscountType.RATE,
                value = 100,
            )
            assertThat(discountAmount.value).isEqualTo(100)
        }
    }
}
