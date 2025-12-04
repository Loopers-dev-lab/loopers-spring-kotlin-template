package com.loopers.domain.coupon

import com.loopers.support.values.Money
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@DisplayName("DiscountPolicy 단위 테스트")
class DiscountPolicyTest {

    @DisplayName("FixedAmountPolicy")
    @Nested
    inner class FixedAmountPolicyTest {

        private val policy = FixedAmountPolicy()

        @DisplayName("FIXED_AMOUNT 타입 쿠폰을 지원한다")
        @Test
        fun `supports returns true for FIXED_AMOUNT type`() {
            // given
            val coupon = createCoupon(DiscountType.FIXED_AMOUNT, 5000)

            // when
            val result = policy.supports(coupon)

            // then
            assertThat(result).isTrue()
        }

        @DisplayName("RATE 타입 쿠폰은 지원하지 않는다")
        @Test
        fun `supports returns false for RATE type`() {
            // given
            val coupon = createCoupon(DiscountType.RATE, 10)

            // when
            val result = policy.supports(coupon)

            // then
            assertThat(result).isFalse()
        }

        @DisplayName("할인 금액이 주문 금액보다 작으면 할인 금액을 반환한다")
        @Test
        fun `calculate returns discount value when less than order amount`() {
            // given
            val coupon = createCoupon(DiscountType.FIXED_AMOUNT, 5000)
            val orderAmount = Money.krw(10000)

            // when
            val result = policy.calculate(orderAmount, coupon)

            // then
            assertThat(result).isEqualTo(Money.krw(5000))
        }

        @DisplayName("할인 금액이 주문 금액보다 크면 주문 금액을 반환한다")
        @Test
        fun `calculate returns order amount when discount exceeds order`() {
            // given
            val coupon = createCoupon(DiscountType.FIXED_AMOUNT, 15000)
            val orderAmount = Money.krw(10000)

            // when
            val result = policy.calculate(orderAmount, coupon)

            // then
            assertThat(result).isEqualTo(Money.krw(10000))
        }

        @DisplayName("할인 금액과 주문 금액이 같으면 주문 금액을 반환한다")
        @Test
        fun `calculate returns order amount when discount equals order`() {
            // given
            val coupon = createCoupon(DiscountType.FIXED_AMOUNT, 10000)
            val orderAmount = Money.krw(10000)

            // when
            val result = policy.calculate(orderAmount, coupon)

            // then
            assertThat(result).isEqualTo(Money.krw(10000))
        }
    }

    @DisplayName("RatePolicy")
    @Nested
    inner class RatePolicyTest {

        private val policy = RatePolicy()

        @DisplayName("RATE 타입 쿠폰을 지원한다")
        @Test
        fun `supports returns true for RATE type`() {
            // given
            val coupon = createCoupon(DiscountType.RATE, 10)

            // when
            val result = policy.supports(coupon)

            // then
            assertThat(result).isTrue()
        }

        @DisplayName("FIXED_AMOUNT 타입 쿠폰은 지원하지 않는다")
        @Test
        fun `supports returns false for FIXED_AMOUNT type`() {
            // given
            val coupon = createCoupon(DiscountType.FIXED_AMOUNT, 5000)

            // when
            val result = policy.supports(coupon)

            // then
            assertThat(result).isFalse()
        }

        @DisplayName("정률 할인을 올바르게 계산한다")
        @Test
        fun `calculate computes correct percentage`() {
            // given
            val coupon = createCoupon(DiscountType.RATE, 10)
            val orderAmount = Money.krw(10000)

            // when
            val result = policy.calculate(orderAmount, coupon)

            // then
            assertThat(result).isEqualTo(Money.krw(1000))
        }

        @DisplayName("정률 할인 계산 결과는 주문 금액을 초과할 수 없다")
        @Test
        fun `calculate result does not exceed order amount`() {
            // given
            val coupon = createCoupon(DiscountType.RATE, 100)
            val orderAmount = Money.krw(10000)

            // when
            val result = policy.calculate(orderAmount, coupon)

            // then
            assertThat(result).isEqualTo(Money.krw(10000))
        }

        @DisplayName("정률 할인 계산 결과는 정수(원 단위)로 반올림된다")
        @ParameterizedTest(name = "{0}원의 {1}% 할인 = {2}원 (계산값: {3})")
        @CsvSource(
            "10001, 15, 1500, 1500.15",
            "10003, 15, 1500, 1500.45",
            "10004, 15, 1501, 1500.60",
            "10007, 15, 1501, 1501.05",
            "10000, 33, 3300, 3300.00",
            "9999, 10, 1000, 999.90",
            "9995, 10, 1000, 999.50",
            "9994, 10, 999, 999.40",
        )
        fun `calculate rounds to integer won`(
            orderAmount: Long,
            discountRate: Long,
            expectedDiscount: Long,
            calculatedValue: String,
        ) {
            // given
            val coupon = createCoupon(DiscountType.RATE, discountRate)

            // when
            val result = policy.calculate(Money.krw(orderAmount), coupon)

            // then
            assertThat(result).isEqualTo(Money.krw(expectedDiscount))
        }
    }

    private fun createCoupon(type: DiscountType, value: Long): Coupon {
        return Coupon.of(
            name = "테스트 쿠폰",
            discountAmount = DiscountAmount(type = type, value = value),
        )
    }
}
