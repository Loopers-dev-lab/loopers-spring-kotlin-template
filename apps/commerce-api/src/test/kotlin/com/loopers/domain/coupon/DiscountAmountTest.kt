package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.values.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("DiscountAmount 단위 테스트")
class DiscountAmountTest {

    @DisplayName("정액 할인 계산")
    @Nested
    inner class FixedAmountDiscount {

        @DisplayName("할인 금액이 주문 금액보다 작으면 할인 금액을 반환한다")
        @Test
        fun `calculate discount when discount amount is less than order amount`() {
            // given
            val discountAmount = DiscountAmount(
                type = DiscountType.FIXED_AMOUNT,
                value = 5000,
            )
            val orderAmount = Money.krw(10000)

            // when
            val result = discountAmount.calculate(orderAmount)

            // then
            assertThat(result).isEqualTo(Money.krw(5000))
        }

        @DisplayName("할인 금액이 주문 금액보다 크면 주문 금액을 반환한다")
        @Test
        fun `calculate discount when discount amount is greater than order amount`() {
            // given
            val discountAmount = DiscountAmount(
                type = DiscountType.FIXED_AMOUNT,
                value = 15000,
            )
            val orderAmount = Money.krw(10000)

            // when
            val result = discountAmount.calculate(orderAmount)

            // then
            assertThat(result).isEqualTo(Money.krw(10000))
        }

        @DisplayName("할인 금액과 주문 금액이 같으면 주문 금액을 반환한다")
        @Test
        fun `calculate discount when discount amount equals order amount`() {
            // given
            val discountAmount = DiscountAmount(
                type = DiscountType.FIXED_AMOUNT,
                value = 10000,
            )
            val orderAmount = Money.krw(10000)

            // when
            val result = discountAmount.calculate(orderAmount)

            // then
            assertThat(result).isEqualTo(Money.krw(10000))
        }
    }

    @DisplayName("정률 할인 계산")
    @Nested
    inner class RateDiscount {

        @DisplayName("정률 할인을 올바르게 계산한다")
        @Test
        fun `calculate rate discount correctly`() {
            // given
            // 10%
            val discountAmount = DiscountAmount(
                type = DiscountType.RATE,
                value = 10,
            )
            val orderAmount = Money.krw(10000)

            // when
            val result = discountAmount.calculate(orderAmount)

            // then
            assertThat(result).isEqualTo(Money.krw(1000))
        }

        @DisplayName("정률 할인 계산 결과는 주문 금액을 초과할 수 없다")
        @Test
        fun `rate discount cannot exceed order amount`() {
            // given
            // 100%
            val discountAmount = DiscountAmount(
                type = DiscountType.RATE,
                value = 100,
            )
            val orderAmount = Money.krw(10000)

            // when
            val result = discountAmount.calculate(orderAmount)

            // then
            assertThat(result).isEqualTo(Money.krw(10000))
        }

        @DisplayName("정률 할인 계산 시 소수점 이하는 버린다")
        @Test
        fun `rate discount calculation truncates decimal places`() {
            // given
            // 15%
            val discountAmount = DiscountAmount(
                type = DiscountType.RATE,
                value = 15,
            )
            val orderAmount = Money.krw(10001)

            // when
            val result = discountAmount.calculate(orderAmount)

            // then
            // 10001 * 15 / 100 = 1500.15 -> 1500
            assertThat(result).isEqualTo(Money.krw(1500))
        }
    }

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
